package net.corda.finance.obligation.client.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.SendStateAndRefFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap
import net.corda.finance.obligation.client.getLinearStateById
import net.corda.finance.obligation.contracts.Obligation
import net.corda.finance.obligation.flows.AbstractSendToSettlementOracle
import net.corda.finance.obligation.types.OffLedgerSettlementInstructions

@StartableByRPC
class SendToSettlementOracle(val linearId: UniqueIdentifier) : AbstractSendToSettlementOracle() {

    @Suspendable
    override fun call(): SignedTransaction {
        val obligationStateAndRef = getLinearStateById<Obligation.State<*>>(linearId, serviceHub)
                ?: throw IllegalArgumentException("LinearId not recognised.")
        val obligationState = obligationStateAndRef.state.data
        val settlementInstructions = obligationState.settlementInstructions as OffLedgerSettlementInstructions<*>
        val session = initiateFlow(settlementInstructions.settlementOracle)
        subFlow(SendStateAndRefFlow(session, listOf(obligationStateAndRef)))
        val stx = session.receive<SignedTransaction>().unwrap { it }
        return subFlow(FinalityFlow(stx))
    }

}