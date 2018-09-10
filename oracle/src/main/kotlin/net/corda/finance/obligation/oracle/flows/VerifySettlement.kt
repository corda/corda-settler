package net.corda.finance.obligation.oracle.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.ReceiveTransactionFlow
import net.corda.core.node.StatesToRecord
import net.corda.finance.obligation.contracts.Obligation
import net.corda.finance.obligation.oracle.services.Service

@InitiatedBy(AbstractSettleObligation::class)
class VerifySettlement(val otherSession: FlowSession) : FlowLogic<Unit>() {

    private fun checkPaymentSettled(transactionHash: SecureHash) {
        val service = serviceHub.cordaService(Service::class.java)
        val nodes = service.nodes
        val rippleClient = RippleClientForVerification(nodes.first())
        //val rippleClient = RippleClientForVerification(rippleNodes.first())
        val transactionData = rippleClient.transaction(transactionHash.toString())
        transactionData.hasSucceeded()
        // TODO: Check it is paid to the specified account.
        // TODO: Check that the server is up to date.
        // TODO: Do this for all the specified ripple nodes.
        // TODO: Check the right amount has been paid.
        // TODO: Check the payment contains a hash of the obligation's linear ID.

        // TODO: If the payment is successful...
        // Then create a new transaction that extinguishes the obligation.
        // The Oracle signs it.
        // Distribute it to both participants.
    }

    private fun handleRippleSettlement(obligation: Obligation.State<*>, settlementInstructions: RippleSettlementInstructions) {
        val transactionHash = settlementInstructions.rippleTransactionHash
                ?: throw IllegalStateException("No transaction hash has been specified yet.")
        checkPaymentSettled(transactionHash)
    }

    @Suspendable
    override fun call() {
        // 1. Receive the signed transaction containing the obligation with settlement instructions.
        val stx = subFlow(ReceiveTransactionFlow(
                otherSideSession = otherSession,
                checkSufficientSignatures = true,
                statesToRecord = StatesToRecord.NONE
        ))

        // Extract the obligation and the settlement instructions.
        val obligationStateAndRef = stx.tx.outRefsOfType<Obligation.State<*>>().single()
        val obligation = obligationStateAndRef.state.data
        val settlementInstructions = obligation.settlementInstructions

        // Check there are settlement instructions.
        check(settlementInstructions != null) { "This obligation has no settlement instructions." }

        when (settlementInstructions) {
            is RippleSettlementInstructions -> handleRippleSettlement(obligation, settlementInstructions)
            else -> throw IllegalStateException("Unknown settlment method.")
        }
    }

}