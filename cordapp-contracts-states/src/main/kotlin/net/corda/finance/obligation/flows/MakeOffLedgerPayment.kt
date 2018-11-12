package net.corda.finance.obligation.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.SignedTransaction
import net.corda.finance.obligation.contracts.Obligation
import net.corda.finance.obligation.types.OffLedgerSettlementInstructions
import net.corda.finance.obligation.types.PaymentReference

abstract class MakeOffLedgerPayment(
        val obligationStateAndRef: StateAndRef<Obligation.State<*>>,
        open val settlementInstructions: OffLedgerSettlementInstructions<*>
) : FlowLogic<SignedTransaction>() {

    abstract fun checkBalance(requiredAmount: Amount<*>)

    abstract fun makePayment(obligation: Obligation.State<*>): PaymentReference

    abstract fun checkPaymentNotAlreadyMade(obligation: Obligation.State<*>)

    @Suspendable
    override fun call(): SignedTransaction {
        // 1. This flow should only be started by the beneficiary.
        val obligation = obligationStateAndRef.state.data
        val identityResolver = { abstractParty: AbstractParty ->
            serviceHub.identityService.requireWellKnownPartyFromAnonymous(abstractParty)
        }
        val obligor = obligation.withWellKnownIdentities(identityResolver).obligor
        check(ourIdentity == obligor) { "This flow can only be started by the obligor. " }

        // 2. Check balance.
        checkBalance(obligation.faceAmount)

        // 3. Check payment has not already been made.
        checkPaymentNotAlreadyMade(obligation)

        // 4. Make payment.
        val paymentReference = makePayment(obligation)

        // 5. Add payment reference to settlement instructions and update state.
        return subFlow(UpdateObligationWithPaymentRef(obligation.linearId, paymentReference))
    }
}