package com.r3.corda.finance.obligation.client.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.finance.obligation.types.OffLedgerPayment
import com.r3.corda.finance.obligation.types.SettlementOracleResult
import com.r3.corda.finance.obligation.client.getLinearStateById
import com.r3.corda.finance.obligation.flows.AbstractSendToSettlementOracle
import com.r3.corda.finance.obligation.states.Obligation
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.CollectSignaturesFlow
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.SendStateAndRefFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.unwrap

@StartableByRPC
class SendToSettlementOracle(
        val linearId: UniqueIdentifier,
        override val progressTracker: ProgressTracker = SendToSettlementOracle.tracker()
) : AbstractSendToSettlementOracle() {

    companion object {
        object INITIALISING : ProgressTracker.Step("Performing initial steps.")
        object SENDING : ProgressTracker.Step("Sending obligation to settlement oracle.")
        object WAITING : ProgressTracker.Step("Waiting for response from settlement oracle.")
        object FINALISING : ProgressTracker.Step("Finalising transaction.") {
            override fun childProgressTracker(): ProgressTracker = FinalityFlow.tracker()
        }

        fun tracker() = ProgressTracker(INITIALISING, SENDING, WAITING)
    }

    @Suspendable
    override fun call(): SignedTransaction {
        progressTracker.currentStep = INITIALISING
        // Resolve the linearId to an obligation.
        val obligationStateAndRef = getLinearStateById<Obligation<*>>(linearId, serviceHub)
                ?: throw IllegalArgumentException("LinearId not recognised.")

        // Get the Oracle from the settlement instructions.
        val obligationState = obligationStateAndRef.state.data
        val settlementMethod = obligationState.settlementMethod as OffLedgerPayment<*>

        // Send the Oracle the ObligationContract state.
        progressTracker.currentStep = SENDING
        val session = initiateFlow(settlementMethod.settlementOracle)
        subFlow(SendStateAndRefFlow(session, listOf(obligationStateAndRef)))

        // Receive a SignedTransaction from the oracle that exits the obligation, or throw an exception if we timed out.
        progressTracker.currentStep = WAITING
        return session.receive<SettlementOracleResult>().unwrap {
            when (it) {
                is SettlementOracleResult.Success -> {
                    val stx = it.stx
                    subFlow(FinalityFlow(stx, FINALISING.childProgressTracker()))
                }
                is SettlementOracleResult.Failure -> {
                    throw IllegalStateException(it.message)
                }
            }
        }
    }

}