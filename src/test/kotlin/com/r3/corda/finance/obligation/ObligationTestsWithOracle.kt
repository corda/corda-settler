package com.r3.corda.finance.obligation

import com.r3.corda.finance.obligation.client.flows.CreateObligation
import com.r3.corda.finance.obligation.client.flows.SendToSettlementOracle
import com.r3.corda.finance.obligation.commands.ObligationCommands
import com.r3.corda.finance.obligation.contracts.ObligationContract
import com.r3.corda.finance.obligation.states.Obligation
import com.r3.corda.finance.obligation.types.DigitalCurrency
import com.r3.corda.finance.obligation.types.Money
import com.r3.corda.finance.obligation.types.PaymentStatus
import com.r3.corda.finance.ripple.services.XRPService
import com.r3.corda.finance.ripple.types.XrpPayment
import com.r3.corda.finance.ripple.types.XrpSettlement
import com.r3.corda.finance.ripple.utilities.XRP
import com.ripple.core.coretypes.AccountID
import net.corda.core.contracts.Amount
import net.corda.core.flows.FinalityFlow
import net.corda.core.transactions.TransactionBuilder
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
    fun `novate obligation currency`() {
        // Create obligation.
        val newTransaction = A.createObligation(10000.USD, B, CreateObligation.InitiatorRole.OBLIGOR).getOrThrow()
        val obligation = newTransaction.singleOutput<Obligation<DigitalCurrency>>()
        val obligationId = obligation.linearId()

        val novationCommand = ObligationCommands.Novate.UpdateFaceAmountToken<Money, Money>(
                oldToken = USD,
                newToken = XRP,
                oracle = O.legalIdentity(),
                fxRate = null
        )

        val result = A.transaction { A.novateObligation(obligationId, novationCommand).getOrThrow() }
        val novatedObligation = result.singleOutput<Obligation<Money>>()
        assertEquals(XRP, novatedObligation.state.data.faceAmount.token)
    }

    @Test
    fun `create new obligation and add settlement instructions`() {
        // Create obligation.
        val newObligation = A.createObligation(10000.XRP, B, CreateObligation.InitiatorRole.OBLIGOR).getOrThrow()
        val obligation = newObligation.singleOutput<Obligation<DigitalCurrency>>()
        val obligationId = obligation.linearId()

        // Add settlement instructions.
        val rippleAddress = AccountID.fromString("rNmkj4AtjEHJh3D9hMRC4rS3CXQ9mX4S4b")
        // Just use party A as the oracle for now.
        val settlementInstructions = XrpSettlement(rippleAddress, A.legalIdentity())

        // Add the settlement instructions.
        val updatedObligation = B.addSettlementInstructions(obligationId, settlementInstructions).getOrThrow()
        val transactionHash = updatedObligation.id

        // Wait for the updates on both nodes.
        val aObligation = A.watchForTransaction(transactionHash).toCompletableFuture()
        val bObligation = B.watchForTransaction(transactionHash).toCompletableFuture()
        CompletableFuture.allOf(aObligation, bObligation)
    }

    @Test
    fun `end to end test with single payment`() {
        // Create obligation.
        val newObligation = A.createObligation(10.XRP, B, CreateObligation.InitiatorRole.OBLIGOR).getOrThrow()
        val obligation = newObligation.singleOutput<Obligation<DigitalCurrency>>()
        val obligationId = obligation.linearId()

        // Add settlement instructions.
        val xrpAddress = AccountID.fromString("ra6mzL1Xy9aN5eRdjzn9CHTMwcczG1uMpN")
        val settlementInstructions = XrpSettlement(xrpAddress, O.legalIdentity())

        // Add the settlement instructions.
        B.addSettlementInstructions(obligationId, settlementInstructions).getOrThrow()

        // Make the payment.
        val obligationWithPaymentMade = A.transaction { A.makePayment(10.XRP, obligationId).getOrThrow() }
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
    fun `partial settlement`() {
        // Create obligation.
        val newObligation = A.createObligation(10.XRP, B, CreateObligation.InitiatorRole.OBLIGOR).getOrThrow()
        val obligation = newObligation.singleOutput<Obligation<DigitalCurrency>>()
        val obligationId = obligation.linearId()

        // Add settlement instructions.
        val xrpAddress = AccountID.fromString("ra6mzL1Xy9aN5eRdjzn9CHTMwcczG1uMpN")
        val settlementInstructions = XrpSettlement(xrpAddress, O.legalIdentity())

        // Add the settlement instructions.
        B.addSettlementInstructions(obligationId, settlementInstructions).getOrThrow()

        // Make the payment.
        val obligationWithPaymentMade = A.transaction { A.makePayment(5.XRP, obligationId).getOrThrow() }
        val transactionHash = obligationWithPaymentMade.id

        // Wait for the updates on both nodes.
        val aObligation = A.watchForTransaction(transactionHash).toCompletableFuture()
        val bObligation = B.watchForTransaction(transactionHash).toCompletableFuture()
        CompletableFuture.allOf(aObligation, bObligation)

        // Print settled obligation info.
        val partiallySettledObligation = A.queryObligationById(obligationId)
        println(partiallySettledObligation.state.data)
        println(partiallySettledObligation.state.data.settlementMethod as XrpSettlement)
        partiallySettledObligation.state.data.payments.forEach(::println)
        assertEquals(partiallySettledObligation.state.data.settlementStatus, Obligation.SettlementStatus.PARTIALLY_SETTLED)
    }

    @Test
    fun `settle with multiple payments`() {
        // Create obligation.
        val newObligation = A.createObligation(10.XRP, B, CreateObligation.InitiatorRole.OBLIGOR).getOrThrow()
        val obligation = newObligation.singleOutput<Obligation<DigitalCurrency>>()
        val obligationId = obligation.linearId()

        // Add settlement instructions.
        val xrpAddress = AccountID.fromString("ra6mzL1Xy9aN5eRdjzn9CHTMwcczG1uMpN")
        val settlementInstructions = XrpSettlement(xrpAddress, O.legalIdentity())

        // Add the settlement instructions.
        B.addSettlementInstructions(obligationId, settlementInstructions).getOrThrow()

        // Make payment one and two.
        A.transaction { A.makePayment(5.XRP, obligationId).getOrThrow() }
        val obligationWithPaymentMade = A.transaction { A.makePayment(5.XRP, obligationId).getOrThrow() }
        val transactionHash = obligationWithPaymentMade.id

        // Wait for the updates on both nodes.
        val aObligation = A.watchForTransaction(transactionHash).toCompletableFuture()
        val bObligation = B.watchForTransaction(transactionHash).toCompletableFuture()
        CompletableFuture.allOf(aObligation, bObligation)

        // Print settled obligation info.
        val partiallySettledObligation = A.queryObligationById(obligationId)
        println(partiallySettledObligation.state.data)
        println(partiallySettledObligation.state.data.settlementMethod as XrpSettlement)
        partiallySettledObligation.state.data.payments.forEach(::println)
        assertEquals(partiallySettledObligation.state.data.settlementStatus, Obligation.SettlementStatus.SETTLED)
    }

    @Test
    fun `No payments made`() {
        // Create obligation.
        val newObligation = A.createObligation(10000.XRP, B, CreateObligation.InitiatorRole.OBLIGOR).getOrThrow()
        val obligation = newObligation.singleOutput<Obligation<DigitalCurrency>>()
        val obligationId = obligation.linearId()

        // Add settlement instructions.
        val xrpAddress = AccountID.fromString("ra6mzL1Xy9aN5eRdjzn9CHTMwcczG1uMpN")
        val settlementInstructions = XrpSettlement(xrpAddress, O.legalIdentity())

        // Add the settlement instructions.
        B.addSettlementInstructions(obligationId, settlementInstructions).getOrThrow()

        // Wait for the updates on both nodes.
        assertFailsWith<IllegalStateException>("No payments have been made for this obligation.") {
            B.startFlow(SendToSettlementOracle(obligationId)).getOrThrow()
        }
    }

    @Test
    fun `Payment not made by deadline`() {
        // Create obligation.
        val newObligation = A.createObligation(10000.XRP, B, CreateObligation.InitiatorRole.OBLIGOR).getOrThrow()
        val obligation = newObligation.singleOutput<Obligation<DigitalCurrency>>()
        val obligationId = obligation.linearId()

        // Add settlement instructions.
        val xrpAddress = AccountID.fromString("ra6mzL1Xy9aN5eRdjzn9CHTMwcczG1uMpN")
        val settlementInstructions = XrpSettlement(xrpAddress, O.legalIdentity())

        // Add the settlement instructions.
        val result = B.addSettlementInstructions(obligationId, settlementInstructions).getOrThrow()

        // Manually update the obligation with a fake payment.
        val latestObligation = result.singleOutput<Obligation<DigitalCurrency>>()
        val fakePayment = XrpPayment(
                paymentReference = "wrong reference",
                lastLedgerSequence = B.ledgerIndex() + 3, // 15 seconds or so.
                status = PaymentStatus.SENT,
                amount = 10.XRP
        )
        val obligationWithFakePayment = latestObligation.state.data.withPayment(fakePayment)
        val notary = B.services.networkMapCache.notaryIdentities.first()
        val stx = B.services.signInitialTransaction(TransactionBuilder(notary = notary).apply {
            addInputState(latestObligation)
            addOutputState(obligationWithFakePayment, ObligationContract.CONTRACT_REF)
            addCommand(ObligationCommands.AddPayment(), B.legalIdentity().owningKey)
        })
        B.startFlow(FinalityFlow(stx)).getOrThrow()

        // Wait for the updates on both nodes.
        assertFailsWith<IllegalStateException>("Payment wasn't made by the deadline.") {
            B.startFlow(SendToSettlementOracle(obligationId)).getOrThrow()
        }
    }

}