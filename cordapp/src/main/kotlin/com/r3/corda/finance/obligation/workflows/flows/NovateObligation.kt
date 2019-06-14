package com.r3.corda.finance.obligation.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.finance.obligation.contracts.ObligationContract
import com.r3.corda.finance.obligation.contracts.commands.ObligationCommands
import com.r3.corda.finance.obligation.contracts.states.Obligation
import com.r3.corda.finance.obligation.contracts.types.FxRateRequest
import com.r3.corda.finance.obligation.contracts.types.FxRateResponse
import com.r3.corda.finance.obligation.workflows.getLinearStateById
import com.r3.corda.finance.obligation.workflows.resolver
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.ProgressTracker
import java.math.BigDecimal

object NovateObligation {

    @InitiatingFlow
    @StartableByRPC
    class Initiator(
            val linearId: UniqueIdentifier,
            private val novationCommand: ObligationCommands.Novate
    ) : FlowLogic<WireTransaction>() {

        companion object {
            object INITIALISING : ProgressTracker.Step("Performing initial steps.")
            object HANDLING : ProgressTracker.Step("Handling novation command.")
            object BUILDING : ProgressTracker.Step("Building and verifying transaction.")
            object SIGNING : ProgressTracker.Step("signing transaction.")

            object COLLECTING : ProgressTracker.Step("Collecting counterparty signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING : ProgressTracker.Step("Finalising transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(INITIALISING, HANDLING, BUILDING, SIGNING, COLLECTING, FINALISING)
        }

        override val progressTracker: ProgressTracker = tracker()

        @Suspendable
        fun handleUpdateFaceAmountToken(obligation: Obligation<TokenType>): Pair<Obligation<TokenType>, ObligationCommands.Novate> {
            // We know that this is a token change.
            novationCommand as ObligationCommands.Novate.UpdateFaceAmountToken<*, *>
            // If no fx rate is supplied then get one from the Oracle.
            val fxRate = if (novationCommand.fxRate == null) {
                val request = FxRateRequest(novationCommand.oldToken, novationCommand.newToken, obligation.createdAt)
                val response: FxRateResponse = subFlow(GetFxRate(request, novationCommand.oracle))
                response.rate
            } else novationCommand.fxRate!!
            // Update the obligation.
            val newQuantity = obligation.faceAmount.toDecimal() * BigDecimal.valueOf(fxRate.toDouble())
            val newAmount = Amount.fromDecimal(newQuantity, novationCommand.newToken)
            return Pair(obligation.withNewFaceValueToken(newAmount), novationCommand.copy(fxRate = fxRate))
        }

        @Suspendable
        fun handleNovationCommand(
                obligationStateAndRef: StateAndRef<Obligation<TokenType>>
        ): Pair<Obligation<TokenType>, ObligationCommands.Novate> {
            val obligation = obligationStateAndRef.state.data
            return when (novationCommand) {
                is ObligationCommands.Novate.UpdateDueBy ->
                    Pair(obligation.withDueByDate(novationCommand.newDueBy), novationCommand)
                is ObligationCommands.Novate.UpdateParty ->
                    Pair(obligation.withNewCounterparty(novationCommand.oldParty, novationCommand.newParty), novationCommand)
                is ObligationCommands.Novate.UpdateFaceAmountQuantity ->
                    Pair(obligation.withNewFaceValueQuantity(novationCommand.newAmount), novationCommand)
                is ObligationCommands.Novate.UpdateFaceAmountToken<*, *> -> handleUpdateFaceAmountToken(obligation)
            }
        }

        @Suspendable
        override fun call(): WireTransaction {
            // Get the obligation from our vault.
            progressTracker.currentStep = INITIALISING
            val obligationStateAndRef = getLinearStateById<Obligation<TokenType>>(linearId, serviceHub)
                    ?: throw IllegalArgumentException("LinearId not recognised.")
            // Generate output and required signers list based based upon supplied command.
            progressTracker.currentStep = HANDLING
            val (novatedObligation, updatedNovationCommand) = handleNovationCommand(obligationStateAndRef)

            // Create the new transaction.
            progressTracker.currentStep = BUILDING
            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val signers = novatedObligation.participants.map { it.owningKey }
            val utx = TransactionBuilder(notary = notary).apply {
                addInputState(obligationStateAndRef)
                addOutputState(novatedObligation, ObligationContract.CONTRACT_REF)
                // Add the oracle key if required.
                if (novationCommand is ObligationCommands.Novate.UpdateFaceAmountToken<*, *>) {
                    val oracleKey = novationCommand.oracle.owningKey
                    addCommand(updatedNovationCommand, signers + oracleKey)
                } else {
                    addCommand(updatedNovationCommand, signers)
                }
            }

            // Get the counterparty and our signing key.
            val obligation = obligationStateAndRef.state.data.withWellKnownIdentities(resolver)
            val (us, counterparty) = if (obligation.obligor == ourIdentity) {
                Pair(novatedObligation.obligor, obligation.obligee)
            } else {
                Pair(novatedObligation.obligee, obligation.obligor)
            }

            // Sign it and get the oracle's signature if required.
            progressTracker.currentStep = SIGNING
            val ptx = if (novationCommand is ObligationCommands.Novate.UpdateFaceAmountToken<*, *>) {
                val selfSignedTransaction = serviceHub.signInitialTransaction(utx, us.owningKey)
                val signature = subFlow(GetFxRateOracleSignature(selfSignedTransaction, novationCommand.oracle))
                selfSignedTransaction + signature
            } else {
                serviceHub.signInitialTransaction(utx, us.owningKey)
            }

            // Get the counterparty's signature.
            progressTracker.currentStep = COLLECTING
            val counterpartyFlow = initiateFlow(counterparty as Party)
            val stx = subFlow(CollectSignaturesFlow(
                    partiallySignedTx = ptx,
                    sessionsToCollectFrom = setOf(counterpartyFlow),
                    myOptionalKeys = listOf(us.owningKey),
                    progressTracker = COLLECTING.childProgressTracker()
            ))

            progressTracker.currentStep = FINALISING
            return subFlow(FinalityFlow(stx, setOf(counterpartyFlow), FINALISING.childProgressTracker())).tx
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(val otherFlow: FlowSession) : FlowLogic<WireTransaction>() {
        @Suspendable
        override fun call(): WireTransaction {
            val flow = object : SignTransactionFlow(otherFlow) {
                @Suspendable
                override fun checkTransaction(stx: SignedTransaction) {
                    // TODO: Do some basic checking here.
                    // Reach out to human operator when HCI is available.
                }
            }
            val stx = subFlow(flow)
            return subFlow(ReceiveFinalityFlow(otherFlow, stx.id)).tx
        }
    }

}
