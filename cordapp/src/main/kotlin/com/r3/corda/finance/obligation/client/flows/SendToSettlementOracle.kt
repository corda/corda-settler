package com.r3.corda.finance.obligation.client.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.finance.obligation.OffLedgerSettlementInstructions
import com.r3.corda.finance.obligation.contracts.Obligation
import com.r3.corda.finance.obligation.flows.AbstractSendToSettlementOracle
import com.r3.corda.finance.obligation.flows.OracleResult
import com.r3.corda.finance.obligation.getLinearStateById
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
        val obligationStateAndRef = getLinearStateById<Obligation.State<*>>(linearId, serviceHub)
                ?: throw IllegalArgumentException("LinearId not recognised.")

        // Get the Oracle from the settlement instructions.
        val obligationState = obligationStateAndRef.state.data
        val settlementInstructions = obligationState.settlementInstructions as OffLedgerSettlementInstructions<*>

        // Send the Oracle the Obligation state.
        val session = initiateFlow(settlementInstructions.settlementOracle)
        subFlow(SendStateAndRefFlow(session, listOf(obligationStateAndRef)))

        // Receive a SignedTransaction from the oracle that exits the obligation, or throw an exception if we timed out.
        return session.receive<OracleResult>().unwrap {
            when (it) {
                is OracleResult.Success -> {
                    val stx = it.stx
                    subFlow(FinalityFlow(stx))
                }
                is OracleResult.Failure -> {
                    throw IllegalStateException(it.message)
                }
            }
        }
    }

}