package com.r3.corda.finance.obligation

import com.r3.corda.finance.obligation.client.flows.CreateObligation
import com.r3.corda.finance.obligation.client.flows.SendToSettlementOracle
import com.r3.corda.finance.obligation.commands.ObligationCommands
import com.r3.corda.finance.obligation.contracts.ObligationContract
import com.r3.corda.finance.obligation.states.Obligation
import com.r3.corda.finance.obligation.types.DigitalCurrency
import com.r3.corda.finance.obligation.types.Money
import com.r3.corda.finance.obligation.types.Payment
import com.r3.corda.finance.obligation.types.PaymentStatus
import com.r3.corda.finance.ripple.services.XRPService
import com.r3.corda.finance.ripple.types.XrpPayment
import com.r3.corda.finance.ripple.types.XrpSettlement
import com.r3.corda.finance.ripple.utilities.XRP
import com.ripple.core.coretypes.AccountID
import net.corda.core.flows.FinalityFlow
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.getOrThrow
import net.corda.finance.AMOUNT
import net.corda.testing.node.StartedMockNode
import org.junit.Test
import java.lang.IllegalArgumentException
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
        return XrpSettlement(xrpAddress, O.legalIdentity())
    }

    @Test
    fun `Payment not made by deadline`() {
        // Create obligation.
        val newObligation = A.createObligation(AMOUNT(10000, currency), B, CreateObligation.InitiatorRole.OBLIGOR).getOrThrow()
        val obligation = newObligation.singleOutput<Obligation<DigitalCurrency>>()
        val obligationId = obligation.linearId()

        // Add settlement instructions.
        val settlementInstructions = createSettlement(O.legalIdentity())

        // Add the settlement instructions.
        val result = B.addSettlementInstructions(obligationId, settlementInstructions).getOrThrow()

        // Manually update the obligation with a fake payment.
        val latestObligation = result.singleOutput<Obligation<Money>>()
        val fakePayment = XrpPayment(
                paymentReference = "wrong reference",
                lastLedgerSequence = B.ledgerIndex() + 3, // 15 seconds or so.
                status = PaymentStatus.SENT,
                amount = 10.XRP
        )
        val obligationWithFakePayment = latestObligation.state.data.withPayment(fakePayment as Payment<Money>)
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