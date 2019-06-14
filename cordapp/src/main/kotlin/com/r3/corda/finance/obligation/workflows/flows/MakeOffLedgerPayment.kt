package com.r3.corda.finance.obligation.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.finance.obligation.contracts.flows.AbstractMakeOffLedgerPayment
import com.r3.corda.finance.obligation.contracts.states.Obligation
import com.r3.corda.finance.obligation.contracts.types.OffLedgerPayment
import com.r3.corda.finance.obligation.contracts.types.Payment
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker

abstract class MakeOffLedgerPayment<T : TokenType>(
        val amount: Amount<T>,
        private val obligationStateAndRef: StateAndRef<Obligation<*>>,
        open val settlementMethod: OffLedgerPayment<*>,
        override val progressTracker: ProgressTracker = MakeOffLedgerPayment.tracker()
) : AbstractMakeOffLedgerPayment() {

    companion object {
        object SETUP : ProgressTracker.Step("Setting up payment method.")
        object CHECKING : ProgressTracker.Step("Checking balance.")
        object PAYING : ProgressTracker.Step("Making payment.")
        object UPDATING : ProgressTracker.Step("Updating obligation with payment details.") {
            override fun childProgressTracker() = UpdateObligationWithPayment.Initiator.tracker()
        }

        fun tracker() = ProgressTracker(SETUP, CHECKING, PAYING, UPDATING)
    }

    @Suspendable
    abstract fun checkBalance(requiredAmount: Amount<*>)

    @Suspendable
    abstract fun makePayment(obligation: Obligation<*>, amount: Amount<T>): Payment<T>

    @Suspendable
    abstract fun setup()

    @Suspendable
    override fun call(): SignedTransaction {
        // 1. This flow should only be started by the beneficiary.
        val obligation = obligationStateAndRef.state.data
        val identityResolver = { abstractParty: AbstractParty ->
            serviceHub.identityService.requireWellKnownPartyFromAnonymous(abstractParty)
        }
        val obligor = obligation.withWellKnownIdentities(identityResolver).obligor
        check(ourIdentity == obligor) { "This flow can only be started by the obligor." }

        // 1. Do any setup stuff.
        progressTracker.currentStep = SETUP
        setup()

        // 2. Check balance.
        progressTracker.currentStep = CHECKING
        checkBalance(amount)

        // 4. Make payment and manually checkpoint
        progressTracker.currentStep = PAYING
        val paymentInformation = makePayment(obligation, amount)

        // 5. Add payment reference to settlement instructions and update state.
        progressTracker.currentStep = UPDATING
        return subFlow(UpdateObligationWithPayment.Initiator(
                linearId = obligation.linearId,
                paymentInformation = paymentInformation,
                progressTracker = UPDATING.childProgressTracker()
        ))
    }
}