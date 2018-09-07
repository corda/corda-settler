package net.corda.finance.obligation.client

import com.ripple.core.coretypes.AccountID
import net.corda.core.utilities.getOrThrow
import net.corda.finance.obligation.client.contracts.Obligation
import net.corda.finance.obligation.client.flows.CreateObligation
import net.corda.finance.obligation.client.types.DigitalCurrency
import net.corda.finance.obligation.client.types.RippleSettlementInstructions
import net.corda.finance.obligation.client.types.XRP
import net.corda.testing.node.internal.TestStartedNode
import org.junit.Before
import org.junit.Test
import java.net.URI
import java.util.concurrent.CompletableFuture

class ObligationTestsWithOracle : MockNetworkTestWithOracle(numberOfNodes = 2) {

    lateinit var A: TestStartedNode
    lateinit var B: TestStartedNode

    @Before
    override fun initialiseNodes() {
        A = nodes[0]
        B = nodes[1]
    }

    @Test
    fun `end to end test`() {
        // Create obligation.
        val newObligation = A.createObligation(10000.XRP, B, CreateObligation.InitiatorRole.OBLIGOR).getOrThrow()
        val obligation = newObligation.singleOutput<Obligation.State<DigitalCurrency>>()
        val obligationId = obligation.linearId()

        // Add settlement instructions.
        val rippleAddress = AccountID.fromString("ra6mzL1Xy9aN5eRdjzn9CHTMwcczG1uMpN")
        val acceptableServers = listOf(URI("http://s.altnet.rippletest.net:51234"))
        val settlementInstructions = RippleSettlementInstructions(rippleAddress, acceptableServers, Oracle.legalIdentity())

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