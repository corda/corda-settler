package com.r3.corda.lib.settler

import com.r3.corda.lib.obligation.contracts.ObligationContract
import com.r3.corda.lib.obligation.contracts.commands.ObligationCommands
import com.r3.corda.lib.obligation.contracts.states.Obligation
import com.r3.corda.lib.obligation.contracts.types.Payment
import com.r3.corda.lib.obligation.contracts.types.PaymentStatus
import com.r3.corda.lib.obligation.workflows.InitiatorRole
import com.r3.corda.lib.settler.ripple.services.XRPService
import com.r3.corda.lib.settler.ripple.types.XrpPayment
import com.r3.corda.lib.settler.ripple.types.XrpSettlement
import com.r3.corda.lib.settler.workflows.flows.SendToSettlementOracle
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.money.XRP
import com.ripple.core.coretypes.AccountID
import net.corda.core.flows.FinalityFlow
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.finance.AMOUNT
import net.corda.testing.node.StartedMockNode
import org.junit.Test
import kotlin.test.assertFailsWith

class XRPObligationTestsWithOracle : AbstractObligationTestsWithOracle<XrpSettlement>(XRP) {
    override fun castToSettlementType(obj : Any?) = obj as XrpSettlement?

    private fun StartedMockNode.ledgerIndex(): Long {
        val xrpService = services.cordaService(XRPService::class.java)
        return xrpService.client.ledgerIndex().ledgerCurrentIndex
    }


    override fun createSettlement(party : Party) : XrpSettlement {
        val addressString = when (party) {
            O.legalIdentity() -> "ra6mzL1Xy9aN5eRdjzn9CHTMwcczG1uMpN"
            A.legalIdentity() -> "rNmkj4AtjEHJh3D9hMRC4rS3CXQ9mX4S4b"
            else -> throw IllegalArgumentException("Unsupported party $party")
        }
        val xrpAddress = AccountID.fromString(addressString)
        return XrpSettlement(xrpAddress.toString(), O.legalIdentity())
    }

    @Test
    fun `Payment not made by deadline`() {
        // Create obligation.
        val newObligation = A.createObligation(AMOUNT(10000, currency), B, InitiatorRole.OBLIGOR).getOrThrow()
        val obligation = newObligation.singleOutput<Obligation<TokenType>>()
        val obligationId = obligation.linearId()

        // Add settlement instructions.
        val settlementInstructions = createSettlement(O.legalIdentity())

        // Add the settlement instructions.
        val result = B.addSettlementInstructions(obligationId, settlementInstructions).getOrThrow()

        // Manually update the obligation with a fake payment.
        val latestObligation = result.singleOutput<Obligation<TokenType>>()
        val fakePayment = XrpPayment(
                paymentReference = "wrong reference",
                lastLedgerSequence = B.ledgerIndex() + 3, // 15 seconds or so.
                status = PaymentStatus.SENT,
                amount = 10.XRP
        )
        val obligationWithFakePayment = latestObligation.state.data.withPayment(fakePayment as Payment<TokenType>)
        val notary = B.services.networkMapCache.notaryIdentities.first()
        val stx = B.services.signInitialTransaction(TransactionBuilder(notary = notary).apply {
            addInputState(latestObligation)
            addOutputState(obligationWithFakePayment, ObligationContract.CONTRACT_REF)
            addCommand(ObligationCommands.AddPayment("wrong reference"), B.legalIdentity().owningKey)
        })
        B.startFlow(FinalityFlow(stx)).getOrThrow()

        // Wait for the updates on both nodes.
        assertFailsWith<IllegalStateException>("Payment wasn't made by the deadline.") {
            B.startFlow(SendToSettlementOracle(obligationId)).getOrThrow()
        }
    }
}
