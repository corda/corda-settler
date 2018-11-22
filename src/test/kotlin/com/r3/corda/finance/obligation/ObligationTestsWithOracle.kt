package com.r3.corda.finance.obligation

import com.r3.corda.finance.obligation.client.flows.CreateObligation
import com.r3.corda.finance.obligation.client.flows.SendToSettlementOracle
import com.r3.corda.finance.obligation.states.Obligation
import com.r3.corda.finance.ripple.services.XRPService
import com.r3.corda.finance.ripple.types.XrpSettlement
import com.r3.corda.finance.ripple.utilities.XRP
import com.ripple.core.coretypes.AccountID
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.StartedMockNode
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
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

    private fun StartedMockNode.ledgerIndex(): Long {
        val xrpService = services.cordaService(XRPService::class.java)
        return xrpService.client.ledgerIndex().ledgerCurrentIndex
    }

    @Test
    fun `newly created obligation is stored in vaults of participants`() {
        // Create obligation.
        val newTransaction = A.createObligation(10000.XRP, B, CreateObligation.InitiatorRole.OBLIGOR).getOrThrow()
        val obligation = newTransaction.singleOutput<Obligation<DigitalCurrency>>()
        val obligationId = obligation.linearId()

        // Check both parties have the same obligation.
        val aObligation = A.queryObligationById(obligationId)
        val bObligation = B.queryObligationById(obligationId)
        assertEquals(aObligation, bObligation)
    }

    @Test
    fun `create new obligation and add settlement instructions`() {
        // Create obligation.
        val newObligation = A.createObligation(10000.XRP, B, CreateObligation.InitiatorRole.OBLIGOR).getOrThrow()
        val obligation = newObligation.singleOutput<Obligation<DigitalCurrency>>()
        val obligationId = obligation.linearId()

        // Add settlement instructions.
        val rippleAddress = AccountID.fromString("rNmkj4AtjEHJh3D9hMRC4rS3CXQ9mX4S4b")
        // After 20 ledgers, if a payment is not made, settlement will be considered failed.
        val lastLedgerSequence = A.ledgerIndex() + 20
        // Just use party A as the oracle for now.
        val settlementInstructions = XrpSettlement(rippleAddress, A.legalIdentity(), lastLedgerSequence)

        // Add the settlement instructions.
        val updatedObligation = B.addSettlementInstructions(obligationId, settlementInstructions).getOrThrow()
        val transactionHash = updatedObligation.id

        // Wait for the updates on both nodes.
        val aObligation = A.watchForTransaction(transactionHash).toCompletableFuture()
        val bObligation = B.watchForTransaction(transactionHash).toCompletableFuture()
        CompletableFuture.allOf(aObligation, bObligation)
    }

    @Test
    fun `end to end test`() {
        // Create obligation.
        val newObligation = A.createObligation(10.XRP, B, CreateObligation.InitiatorRole.OBLIGOR).getOrThrow()
        val obligation = newObligation.singleOutput<Obligation<DigitalCurrency>>()
        val obligationId = obligation.linearId()

        // Add settlement instructions.
        val xrpAddress = AccountID.fromString("ra6mzL1Xy9aN5eRdjzn9CHTMwcczG1uMpN")
        val lastLedgerSequence = A.ledgerIndex() + 20
        val settlementInstructions = XrpSettlement(xrpAddress, O.legalIdentity(), lastLedgerSequence)

        // Add the settlement instructions.
        B.addSettlementInstructions(obligationId, settlementInstructions).getOrThrow()

        // Make the payment.
        val obligationWithPaymentMade = A.transaction { A.makePayment(obligationId).getOrThrow() }
        val transactionHash = obligationWithPaymentMade.id

        // Wait for the updates on both nodes.
        val aObligation = A.watchForTransaction(transactionHash).toCompletableFuture()
        val bObligation = B.watchForTransaction(transactionHash).toCompletableFuture()
        CompletableFuture.allOf(aObligation, bObligation)

        // Print settled obligation info.
        val settledObligation = A.queryObligationById(obligationId)
        println(settledObligation.state.data)
        println(settledObligation.state.data.settlementMethod as XrpSettlement)
    }

    @Test
    fun `last ledger sequence is reached`() {
        // Create obligation.
        val newObligation = A.createObligation(10000.XRP, B, CreateObligation.InitiatorRole.OBLIGOR).getOrThrow()
        val obligation = newObligation.singleOutput<Obligation<DigitalCurrency>>()
        val obligationId = obligation.linearId()

        // Add settlement instructions.
        val xrpAddress = AccountID.fromString("ra6mzL1Xy9aN5eRdjzn9CHTMwcczG1uMpN")
        val lastLedgerSequence = A.ledgerIndex()
        val settlementInstructions = XrpSettlement(xrpAddress, O.legalIdentity(), lastLedgerSequence)

        // Add the settlement instructions.
        B.addSettlementInstructions(obligationId, settlementInstructions).getOrThrow()

        // Wait for the updates on both nodes.
        assertFailsWith<IllegalStateException>("Payment wasn't made by the deadline.") {
            B.startFlow(SendToSettlementOracle(obligationId)).getOrThrow()
        }
    }

}