package com.r3.corda.finance.obligation.client.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.finance.obligation.types.Money
import com.r3.corda.finance.obligation.types.OffLedgerPayment
import com.r3.corda.finance.obligation.types.OnLedgerSettlement
import com.r3.corda.finance.obligation.client.getLinearStateById
import com.r3.corda.finance.obligation.states.Obligation
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.ProgressTracker

@StartableByRPC
class OffLedgerSettleObligation<T : Money>(
        private val amount: Amount<T>,
        private val linearId: UniqueIdentifier
) : FlowLogic<WireTransaction>() {

    companion object {
        object INITIALISING : ProgressTracker.Step("Initialising off ledger payment.")
        object PAYING : ProgressTracker.Step("Invoking payment flow.") {
            override fun childProgressTracker() = MakeOffLedgerPayment.tracker()
        }

        object SENDING : ProgressTracker.Step("Sending obligation to settlement oracle.") {
            override fun childProgressTracker() = SendToSettlementOracle.tracker()
        }

        fun tracker() = ProgressTracker(INITIALISING, PAYING, SENDING)
    }

    override val progressTracker: ProgressTracker = tracker()

    private fun getFlowInstance(
            settlementInstructions: OffLedgerPayment<*>,
            obligationStateAndRef: StateAndRef<Obligation<*>>,
            progressTracker: ProgressTracker
    ): FlowLogic<SignedTransaction> {
        val paymentFlowClass = settlementInstructions.paymentFlow

        check(MakeOffLedgerPayment::class.java.isAssignableFrom(paymentFlowClass)) {
            "Specified payment flow does not sub-class MakeOffLedgerPayment. Aborting..."
        }

        val paymentFlowClassConstructor = paymentFlowClass.getDeclaredConstructor(
                Amount::class.java,
                StateAndRef::class.java,
                OffLedgerPayment::class.java,
                ProgressTracker::class.java
        )
        return paymentFlowClassConstructor.newInstance(
                amount,
                obligationStateAndRef,
                settlementInstructions,
                progressTracker
        )
    }

    @Suspendable
    override fun call(): WireTransaction {
        // The settlement instructions determine how this obligation should be settled.
        progressTracker.currentStep = INITIALISING
        val obligationStateAndRef = getLinearStateById<Obligation<*>>(linearId, serviceHub)
                ?: throw IllegalArgumentException("LinearId not recognised.")
        val obligationState = obligationStateAndRef.state.data
        val settlementMethod = obligationState.settlementMethod

        progressTracker.currentStep = PAYING
        when (settlementMethod) {
            is OnLedgerSettlement -> throw IllegalStateException("ObligationContract to be settled on-ledger. Aborting...")
            is OffLedgerPayment<*> -> subFlow(getFlowInstance(settlementMethod, obligationStateAndRef, PAYING.childProgressTracker()))
            else -> throw IllegalStateException("No settlement instructions added to obligation.")
        }

        // Checks the payment settled.
        // We only supply the linear ID because this flow can be called from the shell on its own.
        return subFlow(SendToSettlementOracle(linearId, SENDING.childProgressTracker())).tx
    }

}