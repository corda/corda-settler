package net.corda.finance.obligation.client.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.obligation.client.getLinearStateById
import net.corda.finance.obligation.contracts.Obligation
import net.corda.finance.obligation.types.OffLedgerSettlementTerms
import net.corda.finance.obligation.types.OnLedgerSettlementTerms

@StartableByRPC
class OffLedgerSettleObligation(private val linearId: UniqueIdentifier) : FlowLogic<SignedTransaction>() {

    companion object {
        object DETERMINING : ProgressTracker.Step("Determining settlement method.")
        object SETTLING : ProgressTracker.Step("Settling obligation.")
        object CHECKING : ProgressTracker.Step("Checking settlement.")

        fun tracker() = ProgressTracker(DETERMINING, SETTLING, CHECKING)
    }

    override val progressTracker: ProgressTracker = tracker()

    @Suspendable
    override fun call(): SignedTransaction {
        // The settlement instructions determine how this obligation should be settled.
        progressTracker.currentStep = DETERMINING
        val obligationStateAndRef = getLinearStateById<Obligation.State<*>>(linearId, serviceHub)
                ?: throw IllegalArgumentException("LinearId not recognised.")
        val obligationState = obligationStateAndRef.state.data
        val settlementInstructions = obligationState.settlementInstructions

        // Run the flow which corresponds to the supplied settlement instructions.
        progressTracker.currentStep = SETTLING
        return when (settlementInstructions) {
            is OnLedgerSettlementTerms -> throw IllegalStateException("Obligation to be settled on-ledger. Aborting ")
            is OffLedgerSettlementTerms -> {
                // Makes a Ripple payment and updates the obligation with the Ripple transaction hash.
                val flow = MakeRipplePayment::class.java
                val ftx = subFlow(flow.getDeclaredConstructor(StateAndRef::class.java).newInstance(obligationStateAndRef))
                progressTracker.currentStep = CHECKING
                val potentiallySettledObligation = ftx.tx.outRefsOfType<Obligation.State<*>>().single()
                potentiallySettledObligation.state.data.settlementInstructions
                // Checks the payment settled.
                subFlow(SendToSettlementOracle(potentiallySettledObligation))
            }
            else -> throw IllegalStateException("No settlement instructions added to obligation.")
        }
    }

}