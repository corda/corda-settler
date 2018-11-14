package net.corda.finance.obligation

import com.ripple.core.coretypes.AccountID
import net.corda.core.utilities.getOrThrow
import net.corda.finance.obligation.client.MockNetworkTest
import net.corda.finance.obligation.client.flows.CreateObligation
import net.corda.finance.obligation.client.flows.SendToSettlementOracle
import net.corda.finance.obligation.contracts.Obligation
import net.corda.finance.obligation.types.DigitalCurrency
import net.corda.finance.ripple.types.XRPSettlementInstructions
import net.corda.finance.ripple.utilities.XRP
import net.corda.testing.node.StartedMockNode
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CompletableFuture
import kotlin.test.assertFailsWith

class ObligationTestsWithOracle : MockNetworkTest(numberOfNodes = 3) {

    lateinit var A: StartedMockNode
    lateinit var B: StartedMockNode
    lateinit var O: StartedMockNode

    @Before
    override fun initialiseNodes() {
        A = nodes[0]
        B = nodes[1]
        O = nodes[2]
    }

    @Test
    fun `end to end test`() {
        // Create obligation.
        val newObligation = A.createObligation(10000.XRP, B, CreateObligation.InitiatorRole.OBLIGOR).getOrThrow()
        val obligation = newObligation.singleOutput<Obligation.State<DigitalCurrency>>()
        val obligationId = obligation.linearId()

        // Add settlement instructions.
        val xrpAddress = AccountID.fromString("ra6mzL1Xy9aN5eRdjzn9CHTMwcczG1uMpN")
        val lastLedgerSequence = A.ledgerIndex() + 20
        val settlementInstructions = XRPSettlementInstructions(xrpAddress, O.legalIdentity(), lastLedgerSequence)

        // Add the settlement instructions.
        B.addSettlementInstructions(obligationId, settlementInstructions).getOrThrow()

        // Make the payment.
        val obligationWithPaymentMade = A.makePayment(obligationId).getOrThrow()
        val transactionHash = obligationWithPaymentMade.id

        // Wait for the updates on both nodes.
        val aObligation = A.watchForTransaction(transactionHash).toCompletableFuture()
        val bObligation = B.watchForTransaction(transactionHash).toCompletableFuture()
        CompletableFuture.allOf(aObligation, bObligation)

        // Print settled obligation info.
        val settledObligation = A.queryObligationById(obligationId)
        println(settledObligation.state.data)
        println(settledObligation.state.data.settlementInstructions as XRPSettlementInstructions)
    }

    @Test
    fun `last ledger sequence is reached`() {
        // Create obligation.
        val newObligation = A.createObligation(10000.XRP, B, CreateObligation.InitiatorRole.OBLIGOR).getOrThrow()
        val obligation = newObligation.singleOutput<Obligation.State<DigitalCurrency>>()
        val obligationId = obligation.linearId()

        // Add settlement instructions.
        val xrpAddress = AccountID.fromString("ra6mzL1Xy9aN5eRdjzn9CHTMwcczG1uMpN")
        val lastLedgerSequence = A.ledgerIndex()
        val settlementInstructions = XRPSettlementInstructions(xrpAddress, O.legalIdentity(), lastLedgerSequence)

        // Add the settlement instructions.
        B.addSettlementInstructions(obligationId, settlementInstructions).getOrThrow()

        // Wait for the updates on both nodes.
        assertFailsWith<IllegalStateException>("Payment wasn't made by the deadline.") {
            B.startFlow(SendToSettlementOracle(obligationId)).getOrThrow()
        }
    }

}