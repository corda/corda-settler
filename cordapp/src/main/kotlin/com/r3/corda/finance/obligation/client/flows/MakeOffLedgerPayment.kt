package com.r3.corda.finance.obligation.client.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.finance.obligation.OffLedgerSettlementInstructions
import com.r3.corda.finance.obligation.PaymentReference
import com.r3.corda.finance.obligation.contracts.Obligation
import com.r3.corda.finance.obligation.flows.AbstractMakeOffLedgerPayment
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.SignedTransaction

abstract class MakeOffLedgerPayment(
        val obligationStateAndRef: StateAndRef<Obligation.State<*>>,
        open val settlementInstructions: OffLedgerSettlementInstructions<*>
) : AbstractMakeOffLedgerPayment() {

    @Suspendable
    abstract fun checkBalance(requiredAmount: Amount<*>)

    @Suspendable
    abstract fun makePayment(obligation: Obligation.State<*>): PaymentReference

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
        checkBalance(obligation.faceAmount)

        // 4. Make payment and manually checkpoint
        val paymentReference = makePayment(obligation)

        // 5. Add payment reference to settlement instructions and update state.
        return subFlow(UpdateObligationWithPaymentRef(obligation.linearId, paymentReference))
    }
}