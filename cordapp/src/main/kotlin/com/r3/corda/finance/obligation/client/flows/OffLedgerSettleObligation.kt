package com.r3.corda.finance.obligation.client.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.finance.obligation.OffLedgerPayment
import com.r3.corda.finance.obligation.OnLedgerSettlement
import com.r3.corda.finance.obligation.client.getLinearStateById
import com.r3.corda.finance.obligation.states.Obligation
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction

@StartableByRPC
class OffLedgerSettleObligation(private val linearId: UniqueIdentifier) : FlowLogic<SignedTransaction>() {

    private fun getFlowInstance(
            settlementInstructions: OffLedgerPayment<*>,
            obligationStateAndRef: StateAndRef<Obligation<*>>
    ): FlowLogic<SignedTransaction> {
        val paymentFlowClass = settlementInstructions.paymentFlow
        val paymentFlowClassConstructor = paymentFlowClass.getDeclaredConstructor(
                StateAndRef::class.java,
                OffLedgerPayment::class.java
        )
        return paymentFlowClassConstructor.newInstance(obligationStateAndRef, settlementInstructions)
    }

    @Suspendable
    override fun call(): SignedTransaction {
        // The settlement instructions determine how this obligation should be settled.
        val obligationStateAndRef = getLinearStateById<Obligation<*>>(linearId, serviceHub)
                ?: throw IllegalArgumentException("LinearId not recognised.")
        val obligationState = obligationStateAndRef.state.data
        val settlementMethod = obligationState.settlementMethod

        when (settlementMethod) {
            is OnLedgerSettlement -> throw IllegalStateException("ObligationContract to be settled on-ledger. Aborting...")
            is OffLedgerPayment<*> -> subFlow(getFlowInstance(settlementMethod, obligationStateAndRef))
            else -> throw IllegalStateException("No settlement instructions added to obligation.")
        }

        // Checks the payment settled.
        // We only supply the linear ID because this flow can be called from the shell on its own.
        return subFlow(SendToSettlementOracle(linearId))
    }

}