package com.r3.corda.finance.obligation.client.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.finance.obligation.Money
import com.r3.corda.finance.obligation.OffLedgerPayment
import com.r3.corda.finance.obligation.Payment
import com.r3.corda.finance.obligation.PaymentReference
import com.r3.corda.finance.obligation.flows.AbstractMakeOffLedgerPayment
import com.r3.corda.finance.obligation.states.Obligation
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.SignedTransaction

abstract class MakeOffLedgerPayment<T : Money>(
        val amount: Amount<T>,
        private val obligationStateAndRef: StateAndRef<Obligation<*>>,
        open val settlementMethod: OffLedgerPayment<*>
) : AbstractMakeOffLedgerPayment() {

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
        setup()

        // 2. Check balance.
        checkBalance(amount)

        // 4. Make payment and manually checkpoint
        val paymentInformation = makePayment(obligation, amount)

        // 5. Add payment reference to settlement instructions and update state.
        return subFlow(UpdateObligationWithPayment(obligation.linearId, paymentInformation))
    }
}