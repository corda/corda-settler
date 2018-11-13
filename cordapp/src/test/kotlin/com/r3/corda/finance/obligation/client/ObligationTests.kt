package com.r3.corda.finance.obligation.client

import com.r3.corda.finance.obligation.DigitalCurrency
import com.r3.corda.finance.obligation.client.flows.CreateObligation
import com.r3.corda.finance.obligation.contracts.Obligation
import com.r3.corda.finance.ripple.types.XRPSettlementInstructions
import com.r3.corda.finance.ripple.utilities.XRP
import com.ripple.core.coretypes.AccountID
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.StartedMockNode
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals

class ObligationTests : MockNetworkTest(numberOfNodes = 2) {

    lateinit var A: StartedMockNode
    lateinit var B: StartedMockNode

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
        // After 20 ledgers, if a payment is not made, settlement will be considered failed.
        val lastLedgerSequence = A.ledgerIndex() + 20
        // Just use party A as the oracle for now.
        val settlementInstructions = XRPSettlementInstructions(rippleAddress, A.legalIdentity(), lastLedgerSequence)

        // Add the settlement instructions.
        val updatedObligation = B.addSettlementInstructions(obligationId, settlementInstructions).getOrThrow()
        val transactionHash = updatedObligation.id

        // Wait for the updates on both nodes.
        val aObligation = A.watchForTransaction(transactionHash).toCompletableFuture()
        val bObligation = B.watchForTransaction(transactionHash).toCompletableFuture()
        CompletableFuture.allOf(aObligation, bObligation)
    }

}