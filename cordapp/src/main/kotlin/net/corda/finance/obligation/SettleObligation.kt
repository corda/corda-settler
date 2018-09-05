package net.corda.finance.obligation

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.finance.obligation.contracts.Obligation
import net.corda.finance.obligation.types.RippleSettlementInstructions

@StartableByRPC
class SettleObligation(val linearId: UniqueIdentifier) : FlowLogic<Unit>() {

    @Suspendable
    override fun call(): Unit {
        val obligationStateAndRef = getLinearStateById<Obligation.State<*>>(linearId, serviceHub)
                ?: throw IllegalArgumentException("LinearId not recognised.")
        val obligationState = obligationStateAndRef.state.data
        val settlementInstructions = obligationState.settlementTerms

        // Run the flow which corresponds to the supplied settlement instructions.
        when (settlementInstructions) {
            is RippleSettlementInstructions -> subFlow(MakeRipplePayment(obligationStateAndRef))
            else -> throw UnsupportedOperationException("Only Ripple settlement is supported for now.")
        }
    }

}