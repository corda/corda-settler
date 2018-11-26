package com.r3.corda.finance.obligation.client.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.finance.obligation.types.FxRateRequest
import com.r3.corda.finance.obligation.types.Money
import com.r3.corda.finance.obligation.client.getLinearStateById
import com.r3.corda.finance.obligation.client.resolver
import com.r3.corda.finance.obligation.commands.ObligationCommands
import com.r3.corda.finance.obligation.contracts.ObligationContract
import com.r3.corda.finance.obligation.states.Obligation
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.math.BigDecimal
import java.security.PublicKey

object NovateObligation {

    @InitiatingFlow
    @StartableByRPC
    class Initiator(
            val linearId: UniqueIdentifier,
            val novationCommand: ObligationCommands.Novate
    ) : FlowLogic<SignedTransaction>() {

        @Suspendable
        fun handleUpdateFaceAmountToken(obligation: Obligation<Money>): Obligation<Money> {
            // We know that this is a token change.
            novationCommand as ObligationCommands.Novate.UpdateFaceAmountToken<*, *>
            // If no fx rate is supplied then get one from the Oracle.
            val fxRate = if (novationCommand.fxRate == null) {
                val request = FxRateRequest(novationCommand.oldToken, novationCommand.newToken, obligation.createdAt)
                val response = subFlow(GetFxRate(request, novationCommand.oracle))
                response.rate
            } else novationCommand.fxRate!!
            // Update the obligation.
            val newQuantity = obligation.faceAmount.toDecimal() * BigDecimal.valueOf(fxRate.toDouble())
            val newAmount = Amount.fromDecimal(newQuantity, novationCommand.newToken)
            return obligation.withNewFaceValueToken(newAmount)
        }

        @Suspendable
        fun handleNovationCommand(obligationStateAndRef: StateAndRef<Obligation<Money>>): Obligation<Money> {
            val obligation = obligationStateAndRef.state.data
            return when (novationCommand) {
                is ObligationCommands.Novate.UpdateDueBy -> obligation.withDueByDate(novationCommand.newDueBy)
                is ObligationCommands.Novate.UpdateParty -> obligation.withNewCounterparty(novationCommand.oldParty, novationCommand.newParty)
                is ObligationCommands.Novate.UpdateFaceAmountQuantity -> obligation.withNewFaceValueQuantity(novationCommand.newAmount)
                is ObligationCommands.Novate.UpdateFaceAmountToken<*, *> -> handleUpdateFaceAmountToken(obligation)
            }
        }

        @Suspendable
        override fun call(): SignedTransaction {
            // Get the obligation from our vault.
            val obligationStateAndRef = getLinearStateById<Obligation<Money>>(linearId, serviceHub)
                    ?: throw IllegalArgumentException("LinearId not recognised.")
            // Generate output and required signers list based based upon supplied command.
            val novatedObligation = handleNovationCommand(obligationStateAndRef)

            // Create the new transaction.
            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val signers = novatedObligation.participants.map { it.owningKey }
            val utx = TransactionBuilder(notary = notary).apply {
                addInputState(obligationStateAndRef)
                addOutputState(novatedObligation, ObligationContract.CONTRACT_REF)
                addCommand(novationCommand, signers)
            }

            // Get the counterparty and our signing key.
            val obligation = obligationStateAndRef.state.data.withWellKnownIdentities(resolver)
            val (us, counterparty) = if (obligation.obligor == ourIdentity) {
                Pair(novatedObligation.obligor, obligation.obligee)
            } else {
                Pair(novatedObligation.obligee, obligation.obligor)
            }

            // Sign it.
            val ptx = serviceHub.signInitialTransaction(utx, us.owningKey)

            // TODO: Collect the oracle's signature.

            // Get the counterparty's signature.
            val couterpartyFlow = initiateFlow(counterparty as Party)
            val stx = subFlow(CollectSignaturesFlow(
                    partiallySignedTx = ptx,
                    sessionsToCollectFrom = setOf(couterpartyFlow),
                    myOptionalKeys = listOf(us.owningKey)
            ))

            return subFlow(FinalityFlow(stx))
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(val otherFlow: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val flow = object : SignTransactionFlow(otherFlow) {
                @Suspendable
                override fun checkTransaction(stx: SignedTransaction) {
                    // TODO: Do some basic checking here.
                    // Reach out to human operator when HCI is available.
                }
            }
            val stx = subFlow(flow)
            // Suspend this flow until the transaction is committed.
            return waitForLedgerCommit(stx.id)
        }
    }

}
