package com.r3.corda.finance.obligation.client.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.finance.obligation.types.Money
import com.r3.corda.finance.obligation.types.Payment
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
import net.corda.core.utilities.ProgressTracker

@StartableByRPC
class UpdateObligationWithPayment<T : Money>(
        val linearId: UniqueIdentifier,
        val paymentInformation: Payment<T>,
        override val progressTracker: ProgressTracker = UpdateObligationWithPayment.tracker()
) : FlowLogic<SignedTransaction>() {

    companion object {
        object INITIALISING : ProgressTracker.Step("Performing initial steps.")
        object ADDING : ProgressTracker.Step("Adding payment information.")
        object BUILDING : ProgressTracker.Step("Building transaction.")
        object SIGNING : ProgressTracker.Step("signing transaction.")

        object FINALISING : ProgressTracker.Step("Finalising transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(INITIALISING, ADDING, BUILDING, SIGNING, FINALISING)
    }

    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = INITIALISING
        val obligationStateAndRef = getLinearStateById<Obligation<T>>(linearId, serviceHub)
                ?: throw IllegalArgumentException("LinearId not recognised.")
        val obligation = obligationStateAndRef.state.data

        // 2. This flow should only be started by the beneficiary.
        val obligor = obligation.withWellKnownIdentities(resolver).obligor
        check(ourIdentity == obligor) { "This flow can only be started by the obligor. " }

        // 3. Add payment to obligation.
        progressTracker.currentStep = ADDING
        val obligationWithNewPayment = obligation.withPayment(paymentInformation)

        // 4. Creating a sign new transaction.
        progressTracker.currentStep = BUILDING
        val signingKey = listOf(obligation.obligor.owningKey)
        val notary = serviceHub.networkMapCache.notaryIdentities.firstOrNull()
                ?: throw FlowException("No available notary.")
        val utx = TransactionBuilder(notary = notary).apply {
            addInputState(obligationStateAndRef)
            addOutputState(obligationWithNewPayment, ObligationContract.CONTRACT_REF)
            addCommand(ObligationCommands.AddPayment(paymentInformation.paymentReference), signingKey)
        }

        // 5. Sign transaction.
        progressTracker.currentStep = SIGNING
        val stx = serviceHub.signInitialTransaction(utx, signingKey)

        // 6. Finalise transaction and send to participants.
        progressTracker.currentStep = FINALISING
        return subFlow(FinalityFlow(stx, FINALISING.childProgressTracker()))
    }
}