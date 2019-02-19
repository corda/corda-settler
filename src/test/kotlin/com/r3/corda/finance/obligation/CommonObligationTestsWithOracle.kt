package com.r3.corda.finance.obligation

import com.r3.corda.finance.obligation.client.flows.CreateObligation
import com.r3.corda.finance.obligation.commands.ObligationCommands
import com.r3.corda.finance.obligation.states.Obligation
import com.r3.corda.finance.obligation.types.DigitalCurrency
import com.r3.corda.finance.obligation.types.Money
import com.r3.corda.finance.ripple.utilities.XRP
import net.corda.core.utilities.getOrThrow
import net.corda.testing.node.StartedMockNode
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

/**
 * This class contains common tests independent from a payment rails
 */
class CommonObligationTestsWithOracle : MockNetworkTest(numberOfNodes = 3) {

    lateinit var A : StartedMockNode
    lateinit var B : StartedMockNode
    lateinit var O : StartedMockNode

    @Before
    override fun initialiseNodes() {
        A = nodes[0]
        B = nodes[1]
        O = nodes[2]
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
}