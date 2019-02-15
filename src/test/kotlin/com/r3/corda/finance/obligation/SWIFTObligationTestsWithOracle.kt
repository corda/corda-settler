package com.r3.corda.finance.obligation

import com.r3.corda.finance.obligation.client.flows.CreateObligation
import com.r3.corda.finance.obligation.states.Obligation
import com.r3.corda.finance.obligation.types.DigitalCurrency
import com.r3.corda.finance.obligation.types.Money
import com.r3.corda.finance.swift.services.SWIFTClient
import com.r3.corda.finance.swift.types.SWIFTPaymentStatusType
import com.r3.corda.finance.swift.types.SwiftPayment
import com.r3.corda.finance.swift.types.SwiftSettlement
import net.corda.core.node.services.queryBy
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.StartedMockNode
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

class SWIFTObligationTestsWithOracle : MockNetworkTest(numberOfNodes = 3) {
    lateinit var A: StartedMockNode
    lateinit var B: StartedMockNode
    lateinit var O: StartedMockNode
    private val creditorName = "Receiving corp"
    private val creditorLei = "6299300D2N76ADNE4Y55"
    private val creditorIban = "BE0473244135"
    private val creditorBicfi = "CITIGB2L"
    private val remittanceInformation = "arn:aws:acm-pca:eu-west-1:522843637103:certificate-authority/e2a9c0fd-b62e-44a9-bcc2-02e46a1f61c2"
    private val obligationDueDate = Instant.now().plusSeconds(10000)

    @Before
    override fun initialiseNodes() {
        A = nodes[0]
        B = nodes[1]
        O = nodes[2]
    }

    private fun updateLastPaymentStatus(delay : Long = 10) {
        val executor = Executors.newSingleThreadScheduledExecutor()
        // manually updating the settlement status in 5 sec
        executor.schedule({
            // we need to craft a swift client manually here because status update endpoint requires a different apiUrl
            val swiftClient = SWIFTClient(
                    "https://gpi.swiftlabapis.com/beta",
                    "zpZxo32bK27q0EVO36B25ETGzaC0SyilThD2Ry00",
                    "PayingCorporate",
                    "5299000J2N45DDNE4Y28",
                    "BE0473244135",
                    "CITIGB2L")

            // we know that there is only one obligation there
            val swiftObligation = A.services.vaultService.queryBy<Obligation<Money>>().states.single()
            val lastPayment = swiftObligation.state.data.payments.last() as  SwiftPayment
            swiftClient.updatePaymentStatus(lastPayment.paymentReference, SWIFTPaymentStatusType.ACCC)
        }, delay, TimeUnit.SECONDS)
    }

    @Test
    fun `end to end test with swift payment`() {
        // Create obligation.
        val newObligation = A.createObligation(10.GBP, B, CreateObligation.InitiatorRole.OBLIGOR, obligationDueDate).getOrThrow()
        val obligation = newObligation.singleOutput<Obligation<DigitalCurrency>>()
        val obligationId = obligation.linearId()

        val settlementInstructions = SwiftSettlement(creditorIban, O.legalIdentity(), creditorName, creditorLei, creditorBicfi, remittanceInformation)

        // Add the settlement instructions.
        B.addSettlementInstructions(obligationId, settlementInstructions).getOrThrow()

        // scheduling to update the last payment status
        updateLastPaymentStatus()

        // Make the payment.
        val obligationWithPaymentMade = A.transaction { A.makePayment(10.GBP, obligationId).getOrThrow() }
        val transactionHash = obligationWithPaymentMade.id

        // Wait for the updates on both nodes.
        val aObligation = A.watchForTransaction(transactionHash).toCompletableFuture()
        val bObligation = B.watchForTransaction(transactionHash).toCompletableFuture()
        CompletableFuture.allOf(aObligation, bObligation)

        // Print settled obligation info.
        val settledObligation = A.queryObligationById(obligationId)
        println(settledObligation.state.data)
        println(settledObligation.state.data.settlementMethod as SwiftSettlement)
    }

    @Test
    fun `partial swift settlement`() {
        // Create obligation.
        val newObligation = A.createObligation(10.GBP, B, CreateObligation.InitiatorRole.OBLIGOR, obligationDueDate).getOrThrow()
        val obligation = newObligation.singleOutput<Obligation<DigitalCurrency>>()
        val obligationId = obligation.linearId()


        val settlementInstructions = SwiftSettlement(creditorIban, O.legalIdentity(), creditorName, creditorLei, creditorBicfi, remittanceInformation)

        // Add the settlement instructions.
        B.addSettlementInstructions(obligationId, settlementInstructions).getOrThrow()

        // scheduling to update the last payment status
        updateLastPaymentStatus()

        // Make the payment.
        val obligationWithPaymentMade = A.transaction { A.makePayment(5.GBP, obligationId).getOrThrow() }
        val transactionHash = obligationWithPaymentMade.id

        // Wait for the updates on both nodes.
        val aObligation = A.watchForTransaction(transactionHash).toCompletableFuture()
        val bObligation = B.watchForTransaction(transactionHash).toCompletableFuture()
        CompletableFuture.allOf(aObligation, bObligation)

        // Print settled obligation info.
        val partiallySettledObligation = A.queryObligationById(obligationId)
        println(partiallySettledObligation.state.data)
        println(partiallySettledObligation.state.data.settlementMethod as SwiftSettlement)
        partiallySettledObligation.state.data.payments.forEach(::println)
        assertEquals(partiallySettledObligation.state.data.settlementStatus, Obligation.SettlementStatus.PARTIALLY_SETTLED)
    }


    @Test
    fun `settle with multiple payments`() {
        // Create obligation.
        val newObligation = A.createObligation(10.GBP, B, CreateObligation.InitiatorRole.OBLIGOR, obligationDueDate).getOrThrow()
        val obligation = newObligation.singleOutput<Obligation<DigitalCurrency>>()
        val obligationId = obligation.linearId()

        val settlementInstructions = SwiftSettlement(creditorIban, O.legalIdentity(), creditorName, creditorLei, creditorBicfi, remittanceInformation)

        // Add the settlement instructions.
        B.addSettlementInstructions(obligationId, settlementInstructions).getOrThrow()

        // scheduling update twice (for multiple payments)
        updateLastPaymentStatus()
        updateLastPaymentStatus(20L)

        // Make payment one and two.
        A.transaction { A.makePayment(5.GBP, obligationId).getOrThrow() }
        val obligationWithPaymentMade = A.transaction { A.makePayment(5.GBP, obligationId).getOrThrow() }
        val transactionHash = obligationWithPaymentMade.id

        // Wait for the updates on both nodes.
        val aObligation = A.watchForTransaction(transactionHash).toCompletableFuture()
        val bObligation = B.watchForTransaction(transactionHash).toCompletableFuture()
        CompletableFuture.allOf(aObligation, bObligation)

        // Print settled obligation info.
        val partiallySettledObligation = A.queryObligationById(obligationId)
        println(partiallySettledObligation.state.data)
        println(partiallySettledObligation.state.data.settlementMethod as SwiftSettlement)
        partiallySettledObligation.state.data.payments.forEach(::println)
        assertEquals(partiallySettledObligation.state.data.settlementStatus, Obligation.SettlementStatus.SETTLED)
    }

}