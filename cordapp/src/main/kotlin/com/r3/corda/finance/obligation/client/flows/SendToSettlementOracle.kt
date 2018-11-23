package com.r3.corda.finance.obligation.client.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.finance.obligation.OffLedgerPayment
import com.r3.corda.finance.obligation.SettlementOracleResult
import com.r3.corda.finance.obligation.client.getLinearStateById
import com.r3.corda.finance.obligation.flows.AbstractSendToSettlementOracle
import com.r3.corda.finance.obligation.states.Obligation
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.SendStateAndRefFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap

@StartableByRPC
class SendToSettlementOracle(val linearId: UniqueIdentifier) : AbstractSendToSettlementOracle() {

    @Suspendable
    override fun call(): SignedTransaction {
        // Resolve the linearId to an obligation.
        val obligationStateAndRef = getLinearStateById<Obligation<*>>(linearId, serviceHub)
                ?: throw IllegalArgumentException("LinearId not recognised.")

        // Get the Oracle from the settlement instructions.
        val obligationState = obligationStateAndRef.state.data
        val settlementMethod = obligationState.settlementMethod as OffLedgerPayment<*>

        // Send the Oracle the ObligationContract state.
        val session = initiateFlow(settlementMethod.settlementOracle)
        subFlow(SendStateAndRefFlow(session, listOf(obligationStateAndRef)))

        // Receive a SignedTransaction from the oracle that exits the obligation, or throw an exception if we timed out.
        return session.receive<SettlementOracleResult>().unwrap {
            when (it) {
                is SettlementOracleResult.Success -> {
                    val stx = it.stx
                    subFlow(FinalityFlow(stx))
                }
                is SettlementOracleResult.Failure -> {
                    throw IllegalStateException(it.message)
                }
            }
        }
    }

}