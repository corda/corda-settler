package com.r3.corda.finance.obligation.client.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.finance.obligation.types.Money
import com.r3.corda.finance.obligation.types.SettlementMethod
import com.r3.corda.finance.obligation.client.getLinearStateById
import com.r3.corda.finance.obligation.client.resolver
import com.r3.corda.finance.obligation.commands.ObligationCommands
import com.r3.corda.finance.obligation.contracts.ObligationContract
import com.r3.corda.finance.obligation.states.Obligation
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.ProgressTracker

@InitiatingFlow
@StartableByRPC
class UpdateSettlementMethod(
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
        val obligationStateAndRef = getLinearStateById<Obligation<Money>>(linearId, serviceHub)
                ?: throw IllegalArgumentException("LinearId not recognised.")
        val obligation = obligationStateAndRef.state.data

        // 2. This flow should only be started by the beneficiary.
        val obligee = obligation.withWellKnownIdentities(resolver).obligee
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
        return subFlow(FinalityFlow(stx, FINALISING.childProgressTracker())).tx
    }

}