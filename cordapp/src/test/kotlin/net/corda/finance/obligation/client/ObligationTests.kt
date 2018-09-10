package net.corda.finance.obligation.client

import com.ripple.core.coretypes.AccountID
import net.corda.core.utilities.getOrThrow
import net.corda.finance.obligation.client.flows.CreateObligation
import net.corda.finance.obligation.contracts.Obligation
import net.corda.finance.obligation.types.DigitalCurrency
import net.corda.finance.obligation.types.XRP
import net.corda.testing.node.internal.TestStartedNode
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals

class ObligationTests : MockNetworkTest(numberOfNodes = 2) {

    lateinit var A: TestStartedNode
    lateinit var B: TestStartedNode

    @Before
    override fun initialiseNodes() {
        A = nodes[0]
        B = nodes[1]
    }

    @Test
    fun `newly created obligation is stored in vaults of participants`() {
        // Create obligation.
        val newTransaction = A.createObligation(10000.XRP, B, CreateObligation.InitiatorRole.OBLIGOR).getOrThrow()
        val obligation = newTransaction.singleOutput<Obligation.State<DigitalCurrency>>()
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
        val obligation = newObligation.singleOutput<Obligation.State<DigitalCurrency>>()
        val obligationId = obligation.linearId()

        // Add settlement instructions.
        val rippleAddress = AccountID.fromString("rNmkj4AtjEHJh3D9hMRC4rS3CXQ9mX4S4b")
        // Just use party A as the oracle for now.
        val settlementInstructions = RippleSettlementInstructions(rippleAddress, A.legalIdentity())

        // Add the settlement instructions.
        val updatedObligation = B.addSettlementInstructions(obligationId, settlementInstructions).getOrThrow()
        val transactionHash = updatedObligation.id

        // Wait for the updates on both nodes.
        val aObligation = A.watchForTransaction(transactionHash).toCompletableFuture()
        val bObligation = B.watchForTransaction(transactionHash).toCompletableFuture()
        CompletableFuture.allOf(aObligation, bObligation)
    }

    @Test
    fun `create new obligation and add settlement instructions then make payment`() {
        // Create obligation.
        val newObligation = A.createObligation(10000.XRP, B, CreateObligation.InitiatorRole.OBLIGOR).getOrThrow()
        val obligation = newObligation.singleOutput<Obligation.State<DigitalCurrency>>()
        val obligationId = obligation.linearId()

        // Add settlement instructions.
        val rippleAddress = AccountID.fromString("ra6mzL1Xy9aN5eRdjzn9CHTMwcczG1uMpN")
        // Just use party A as the oracle for now.
        val settlementInstructions = RippleSettlementInstructions(rippleAddress, A.legalIdentity())

        // Add the settlement instructions.
        B.addSettlementInstructions(obligationId, settlementInstructions).getOrThrow()

        // Make the payment.
        val obligationWithPaymentMade = A.makePayment(obligationId).getOrThrow()
        val transactionHash = obligationWithPaymentMade.id

        // Wait for the updates on both nodes.
        val aObligation = A.watchForTransaction(transactionHash).toCompletableFuture()
        val bObligation = B.watchForTransaction(transactionHash).toCompletableFuture()
        CompletableFuture.allOf(aObligation, bObligation)
        println(obligationWithPaymentMade.singleOutput<Obligation.State<*>>().state.data.settlementInstructions)
    }

}