package com.r3.corda.finance.obligation

import com.r3.corda.finance.obligation.client.flows.CreateObligation
import com.r3.corda.finance.obligation.client.flows.SendToSettlementOracle
import com.r3.corda.finance.obligation.states.Obligation
import com.r3.corda.finance.obligation.types.DigitalCurrency
import com.r3.corda.finance.obligation.types.Money
import com.r3.corda.finance.obligation.types.OffLedgerPayment
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.utilities.getOrThrow
import net.corda.finance.AMOUNT
import net.corda.testing.node.StartedMockNode
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Each new payment rails need to extend from this class to implement basic integration tests
 */
abstract class AbstractObligationTestsWithOracle<out T : OffLedgerPayment<*>>(
        protected val currency : Money
) : MockNetworkTest(numberOfNodes = 3) {

    lateinit var A: StartedMockNode
    lateinit var B: StartedMockNode
    lateinit var O: StartedMockNode

    protected abstract fun createSettlement(party : Party) : T
    protected abstract fun castToSettlementType(obj : Any?) : T?
    // this is required for SWIFT-kind of workflow when payments need to be manually approved
    protected open fun manuallyApprovePayments(numberOfPayments: Int = 1) { }

    @Before
    override fun initialiseNodes() {
        A = nodes[0]
        B = nodes[1]
        O = nodes[2]
    }

    @Test
    fun `create new obligation and add settlement instructions`() {
        // Create obligation.
        val newObligation = A.createObligation(AMOUNT(10000, currency), B, CreateObligation.InitiatorRole.OBLIGOR).getOrThrow()
        val obligation = newObligation.singleOutput<Obligation<DigitalCurrency>>()
        val obligationId = obligation.linearId()

        // Just use party A as the oracle for now.
        val settlementInstructions = createSettlement(A.legalIdentity())

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
        val newObligation = A.createObligation(AMOUNT(10, currency), B, CreateObligation.InitiatorRole.OBLIGOR).getOrThrow()
        val obligation = newObligation.singleOutput<Obligation<DigitalCurrency>>()
        val obligationId = obligation.linearId()

        // Add settlement instructions.
        val settlementInstructions = createSettlement(O.legalIdentity())

        // Add the settlement instructions.
        B.addSettlementInstructions(obligationId, settlementInstructions).getOrThrow()

        manuallyApprovePayments()

        // Make the payment.
        val obligationWithPaymentMade = A.transaction { A.makePayment(AMOUNT(10, currency), obligationId).getOrThrow() }
        val transactionHash = obligationWithPaymentMade.id

        // Wait for the updates on both nodes.
        val aObligation = A.watchForTransaction(transactionHash).toCompletableFuture()
        val bObligation = B.watchForTransaction(transactionHash).toCompletableFuture()
        CompletableFuture.allOf(aObligation, bObligation)

        // Print settled obligation info.
        val settledObligation = A.queryObligationById(obligationId)
        println(settledObligation.state.data)
        println(castToSettlementType(settledObligation.state.data.settlementMethod))
    }

    @Test
    fun `partial settlement`() {
        // Create obligation.
        val newObligation = A.createObligation(AMOUNT(10, currency), B, CreateObligation.InitiatorRole.OBLIGOR).getOrThrow()
        val obligation = newObligation.singleOutput<Obligation<DigitalCurrency>>()
        val obligationId = obligation.linearId()

        // Add settlement instructions.
        val settlementInstructions = createSettlement(O.legalIdentity())

        // Add the settlement instructions.
        B.addSettlementInstructions(obligationId, settlementInstructions).getOrThrow()

        manuallyApprovePayments()

        // Make the payment.
        val obligationWithPaymentMade = A.transaction { A.makePayment(AMOUNT(5, currency), obligationId).getOrThrow() }
        val transactionHash = obligationWithPaymentMade.id

        // Wait for the updates on both nodes.
        val aObligation = A.watchForTransaction(transactionHash).toCompletableFuture()
        val bObligation = B.watchForTransaction(transactionHash).toCompletableFuture()
        CompletableFuture.allOf(aObligation, bObligation)

        // Print settled obligation info.
        val partiallySettledObligation = A.queryObligationById(obligationId)
        println(partiallySettledObligation.state.data)
        println(castToSettlementType(partiallySettledObligation.state.data.settlementMethod))
        partiallySettledObligation.state.data.payments.forEach(::println)
        assertEquals(partiallySettledObligation.state.data.settlementStatus, Obligation.SettlementStatus.PARTIALLY_SETTLED)
    }

    @Test
    fun `settle with multiple payments`() {
        // Create obligation.
        val newObligation = A.createObligation(AMOUNT(10, currency), B, CreateObligation.InitiatorRole.OBLIGOR).getOrThrow()
        val obligation = newObligation.singleOutput<Obligation<DigitalCurrency>>()
        val obligationId = obligation.linearId()

        // Add settlement instructions.
        val settlementInstructions = createSettlement(O.legalIdentity())

        // Add the settlement instructions.
        B.addSettlementInstructions(obligationId, settlementInstructions).getOrThrow()

        manuallyApprovePayments(2)

        // Make payment one and two.
        A.transaction { A.makePayment(AMOUNT(5, currency), obligationId).getOrThrow() }
        val obligationWithPaymentMade = A.transaction { A.makePayment(AMOUNT(5, currency), obligationId).getOrThrow() }
        val transactionHash = obligationWithPaymentMade.id

        // Wait for the updates on both nodes.
        val aObligation = A.watchForTransaction(transactionHash).toCompletableFuture()
        val bObligation = B.watchForTransaction(transactionHash).toCompletableFuture()
        CompletableFuture.allOf(aObligation, bObligation)

        // Print settled obligation info.
        val partiallySettledObligation = A.queryObligationById(obligationId)
        println(partiallySettledObligation.state.data)
        println(castToSettlementType(partiallySettledObligation.state.data.settlementMethod))
        partiallySettledObligation.state.data.payments.forEach(::println)
        assertEquals(partiallySettledObligation.state.data.settlementStatus, Obligation.SettlementStatus.SETTLED)
    }

    @Test
    fun `No payments made`() {
        // Create obligation.
        val newObligation = A.createObligation(AMOUNT(10000, currency), B, CreateObligation.InitiatorRole.OBLIGOR).getOrThrow()
        val obligation = newObligation.singleOutput<Obligation<DigitalCurrency>>()
        val obligationId = obligation.linearId()

        // Add settlement instructions.
        val settlementInstructions = createSettlement(O.legalIdentity())

        // Add the settlement instructions.
        B.addSettlementInstructions(obligationId, settlementInstructions).getOrThrow()

        // Wait for the updates on both nodes.
        assertFailsWith<IllegalStateException>("No payments have been made for this obligation.") {
            B.startFlow(SendToSettlementOracle(obligationId)).getOrThrow()
        }
    }

    @Test
    fun `create obligation then cancel it`() {
        // Create obligation.
        val newTransaction = A.createObligation(AMOUNT(10000, currency), B, CreateObligation.InitiatorRole.OBLIGOR).getOrThrow()
        val obligation = newTransaction.singleOutput<Obligation<DigitalCurrency>>()
        val obligationId = obligation.linearId()

        // Cancel it.
        A.cancelObligation(obligationId).getOrThrow()

        // Check the obligation state has been exited.
        val query = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(obligationId), status = Vault.StateStatus.UNCONSUMED)
        // Hack: It takes a moment for the vaults to update...
        Thread.sleep(100)
        assertEquals(null, A.transaction { A.services.vaultService.queryBy<Obligation<Money>>(query).states.singleOrNull() })
        assertEquals(null, B.transaction { B.services.vaultService.queryBy<Obligation<Money>>(query).states.singleOrNull() })
    }
}