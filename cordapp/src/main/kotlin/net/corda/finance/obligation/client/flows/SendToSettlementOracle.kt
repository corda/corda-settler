package net.corda.finance.obligation.client.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.SendStateAndRefFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.unwrap
import net.corda.finance.obligation.contracts.Obligation
import net.corda.finance.obligation.flows.AbstractSendToSettlementOracle
import net.corda.finance.obligation.getLinearStateById
import net.corda.finance.obligation.types.OffLedgerSettlementInstructions

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

        // Receive a SignedTransaction from the oracle that exits the obligation.
        val stx = session.receive<SignedTransaction>().unwrap { it }
        return subFlow(FinalityFlow(stx))
    }

}