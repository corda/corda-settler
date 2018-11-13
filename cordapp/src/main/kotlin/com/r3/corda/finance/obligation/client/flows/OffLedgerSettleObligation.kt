package com.r3.corda.finance.obligation.client.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.finance.obligation.OffLedgerSettlementInstructions
import com.r3.corda.finance.obligation.OnLedgerSettlementTerms
import com.r3.corda.finance.obligation.contracts.Obligation
import com.r3.corda.finance.obligation.getLinearStateById
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction

@StartableByRPC
class OffLedgerSettleObligation(private val linearId: UniqueIdentifier) : FlowLogic<SignedTransaction>() {

    private fun getFlowInstance(
            settlementInstructions: OffLedgerSettlementInstructions<*>,
            obligationStateAndRef: StateAndRef<Obligation.State<*>>
    ): FlowLogic<SignedTransaction> {
        val paymentFlowClass = settlementInstructions.paymentFlow
        val paymentFlowClassConstructor = paymentFlowClass.getDeclaredConstructor(
                StateAndRef::class.java,
                OffLedgerSettlementInstructions::class.java
        )
        return paymentFlowClassConstructor.newInstance(obligationStateAndRef, settlementInstructions)
    }

    @Suspendable
    override fun call(): SignedTransaction {
        // The settlement instructions determine how this obligation should be settled.
        val obligationStateAndRef = getLinearStateById<Obligation.State<*>>(linearId, serviceHub)
                ?: throw IllegalArgumentException("LinearId not recognised.")
        val obligationState = obligationStateAndRef.state.data
        val settlementInstructions = obligationState.settlementInstructions

        when (settlementInstructions) {
            is OnLedgerSettlementTerms -> throw IllegalStateException("Obligation to be settled on-ledger. Aborting...")
            is OffLedgerSettlementInstructions<*> -> subFlow(getFlowInstance(settlementInstructions, obligationStateAndRef))
            else -> throw IllegalStateException("No settlement instructions added to obligation.")
        }

        // Checks the payment settled.
        // We only supply the linear ID because this flow can be called from the shell on its own.
        return subFlow(SendToSettlementOracle(linearId))
    }

}