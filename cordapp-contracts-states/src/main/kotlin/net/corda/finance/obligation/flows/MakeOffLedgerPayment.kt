package net.corda.finance.obligation.flows

import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.obligation.contracts.Obligation

abstract class MakeOffLedgerPayment(val obligationStateAndRef: StateAndRef<Obligation.State<*>>) : FlowLogic<SignedTransaction>() {

    override val progressTracker: ProgressTracker = tracker()

    companion object {
        object INITIALISING : ProgressTracker.Step("Performing initial steps.")
        object CHECKING_BALANCE : ProgressTracker.Step("Checking for sufficient balance.")
        object MAKING_PAYMENT : ProgressTracker.Step("Making payment.")
        object BUILDING : ProgressTracker.Step("Building and verifying Corda transaction.")
        object SIGNING : ProgressTracker.Step("signing transaction.")

        object FINALISING : ProgressTracker.Step("Finalising transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(INITIALISING, CHECKING_BALANCE, MAKING_PAYMENT, BUILDING, SIGNING, FINALISING)
    }

    abstract fun checkBalance(requiredAmount: Amount<*>)

    abstract fun makePayment(obligation: Obligation.State<*>)

    override fun call(): SignedTransaction {
        progressTracker.currentStep = INITIALISING

        // 1. This flow should only be started by the beneficiary.
        val obligation = obligationStateAndRef.state.data
        val obligor = obligation.withWellKnownIdentities(serviceHub).obligor
        check(ourIdentity == obligor) { "This flow can only be started by the obligor. " }


        // 6. Add updated settlement terms to obligation.
        progressTracker.currentStep = BUILDING
        val signingKey = listOf(obligation.obligor.owningKey)
        val notary = serviceHub.networkMapCache.notaryIdentities.firstOrNull()
                ?: throw FlowException("No available notary.")
        val utx = TransactionBuilder(notary = notary).apply {
            addInputState(obligationStateAndRef)
            addOutputState(obligationWithUpdatedSettlementInstructions, Obligation.CONTRACT_REF)
            addCommand(Obligation.Commands.AddPaymentDetails(), signingKey)
        }

        // 7. Sign transaction.
        progressTracker.currentStep = SIGNING
        val stx = serviceHub.signInitialTransaction(utx, signingKey)

        // 8. Finalise transaction and send to participants.
        progressTracker.currentStep = FINALISING
        return subFlow(FinalityFlow(stx, FINALISING.childProgressTracker()))
    }
}