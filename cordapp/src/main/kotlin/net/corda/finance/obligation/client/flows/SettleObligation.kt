package net.corda.finance.obligation.client.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.obligation.client.contracts.Obligation
import net.corda.finance.obligation.client.getLinearStateById
import net.corda.finance.obligation.client.types.RippleSettlementInstructions

@StartableByRPC
class SettleObligation(val linearId: UniqueIdentifier) : AbstractSettleObligation() {

    override val progressTracker: ProgressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {
        val obligationStateAndRef = getLinearStateById<Obligation.State<*>>(linearId, serviceHub)
                ?: throw IllegalArgumentException("LinearId not recognised.")
        val obligationState = obligationStateAndRef.state.data
        val settlementInstructions = obligationState.settlementInstructions

        // Run the flow which corresponds to the supplied settlement instructions.
        val stx = when (settlementInstructions) {
            is RippleSettlementInstructions -> subFlow(MakeRipplePayment(obligationStateAndRef))
            null -> throw IllegalStateException("No settlement instructions added to obligation.")
            else -> throw UnsupportedOperationException("Only Ripple settlement is supported for now.")
        }

        return stx
    }

}