package com.r3.corda.finance.obligation.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.finance.obligation.contracts.ObligationContract
import com.r3.corda.finance.obligation.contracts.commands.ObligationCommands
import com.r3.corda.finance.obligation.contracts.states.Obligation
import com.r3.corda.finance.obligation.contracts.types.SettlementMethod
import com.r3.corda.finance.obligation.workflows.getLinearStateById
import com.r3.corda.finance.obligation.workflows.resolver
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.ProgressTracker

object UpdateSettlementMethod {

    @InitiatingFlow
    @StartableByRPC
    class Initiator(
            val linearId: UniqueIdentifier,
            private val settlementMethod: SettlementMethod
    ) : FlowLogic<WireTransaction>() {

        companion object {
            object INITIALISING : ProgressTracker.Step("Performing initial steps.")
            object UPDATING : ProgressTracker.Step("Updating obligation with settlement method.")
            object BUILDING : ProgressTracker.Step("Building and verifying transaction.")
            object SIGNING : ProgressTracker.Step("signing transaction.")

            object FINALISING : ProgressTracker.Step("Finalising transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(INITIALISING, UPDATING, BUILDING, SIGNING, FINALISING)
        }

        override val progressTracker: ProgressTracker = tracker()

        @Suspendable
        override fun call(): WireTransaction {
            // 1. Retrieve obligation.
            progressTracker.currentStep = INITIALISING
            val obligationStateAndRef = getLinearStateById<Obligation<TokenType>>(linearId, serviceHub)
                    ?: throw IllegalArgumentException("LinearId not recognised.")
            val obligation = obligationStateAndRef.state.data

            // 2. This flow should only be started by the beneficiary.
            val obligationResolved = obligation.withWellKnownIdentities(resolver)
            val obligor = obligationResolved.obligor
            val obligee = obligationResolved.obligee
            check(ourIdentity == obligee) { "This flow can only be started by the obligee. " }

            // 3. Add settlement instructions.
            progressTracker.currentStep = UPDATING
            val obligationWithSettlementTerms = obligation.withSettlementMethod(settlementMethod)

            // 4. Build transaction which adds settlement terms.
            progressTracker.currentStep = BUILDING
            val signingKey = listOf(obligationWithSettlementTerms.obligee.owningKey)
            val notary = serviceHub.networkMapCache.notaryIdentities.firstOrNull()
                    ?: throw FlowException("No available notary.")
            val utx = TransactionBuilder(notary = notary).apply {
                addInputState(obligationStateAndRef)
                addOutputState(obligationWithSettlementTerms, ObligationContract.CONTRACT_REF)
                addCommand(ObligationCommands.UpdateSettlementMethod(), signingKey)
            }

            // 5. Sign transaction.
            progressTracker.currentStep = SIGNING
            val stx = serviceHub.signInitialTransaction(utx, signingKey)

            // 6. Finalise transaction and send to participants.
            progressTracker.currentStep = FINALISING
            val obligorSession = initiateFlow(obligor as Party)
            return subFlow(FinalityFlow(stx, setOf(obligorSession), FINALISING.childProgressTracker())).tx
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(val otherFlow: FlowSession) : FlowLogic<WireTransaction>() {
        @Suspendable
        override fun call(): WireTransaction {
            return subFlow(ReceiveFinalityFlow(otherFlow)).tx
        }
    }

}