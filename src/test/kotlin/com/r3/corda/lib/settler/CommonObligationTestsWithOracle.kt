package com.r3.corda.lib.settler

import com.r3.corda.lib.obligation.commands.ObligationCommands
import com.r3.corda.lib.obligation.states.Obligation
import com.r3.corda.lib.obligation.workflows.InitiatorRole
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.money.USD
import com.r3.corda.lib.tokens.money.XRP
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
        val newTransaction = A.createObligation(10000 of XRP, B, InitiatorRole.OBLIGOR).getOrThrow()
        val obligation = newTransaction.singleOutput<Obligation<TokenType>>()
        val obligationId = obligation.linearId()

        // Check both parties have the same obligation.
        val aObligation = A.queryObligationById(obligationId)
        val bObligation = B.queryObligationById(obligationId)
        assertEquals(aObligation, bObligation)
    }

    @Test
    fun `novate obligation currency`() {
        // Create obligation.
        val newTransaction = A.createObligation(10000.USD, B, InitiatorRole.OBLIGOR).getOrThrow()
        val obligation = newTransaction.singleOutput<Obligation<TokenType>>()
        val obligationId = obligation.linearId()

        val novationCommand = ObligationCommands.Novate.UpdateFaceAmountToken(
                oldToken = USD,
                newToken = XRP,
                oracle = O.legalIdentity(),
                fxRate = null
        )

        val result = A.transaction { A.novateObligation(obligationId, novationCommand).getOrThrow() }
        val novatedObligation = result.singleOutput<Obligation<TokenType>>()
        assertEquals(XRP, novatedObligation.state.data.faceAmount.token)
    }
}
