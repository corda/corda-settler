package net.corda.finance.ripple.flows

import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.SecureHash
import net.corda.finance.obligation.contracts.Obligation
import net.corda.finance.obligation.flows.MakeOffLedgerPayment
import net.corda.finance.obligation.types.OffLedgerSettlementInstructions
import net.corda.finance.obligation.types.PaymentReference
import net.corda.finance.ripple.services.XRPService
import net.corda.finance.ripple.types.XRPSettlementInstructions
import net.corda.finance.ripple.utilities.DEFAULT_XRP_FEE
import net.corda.finance.ripple.utilities.toXRPAmount
import net.corda.finance.ripple.utilities.toXRPHash
import com.ripple.core.coretypes.Amount as RippleAmount

/**

For testing...

Address     ra6mzL1Xy9aN5eRdjzn9CHTMwcczG1uMpN
Secret      sasKgJbTbka3ahFew2BZybfNg494C
Balance     10,000 XRP

Address     rNmkj4AtjEHJh3D9hMRC4rS3CXQ9mX4S4b
Secret      ssn8cYYksFFexYq97sw9UnvLnMKgh
Balance     10,000 XRP
 */
class MakeXRPPayment(
        obligationStateAndRef: StateAndRef<Obligation.State<*>>,
        override val settlementInstructions: OffLedgerSettlementInstructions<*>
) : MakeOffLedgerPayment(obligationStateAndRef, settlementInstructions) {

    override fun checkBalance(requiredAmount: Amount<*>) {
        // Get a XRPService client.
        val xrpClient = serviceHub.cordaService(XRPService::class.java).client

        // Check the balance on the supplied XRPService address.
        val ourAccountInfo = xrpClient.accountInfo(xrpClient.address)
        val balance = ourAccountInfo.accountData.balance
        check(balance > requiredAmount.toXRPAmount()) {
            "You do not have enough XRP to make the payment."
        }
    }

    // We don't want to serialise any of this stuff not @Suspendable.
    override fun makePayment(obligation: Obligation.State<*>): PaymentReference {
        // Get a XRPService client.
        val xrpClient = serviceHub.cordaService(XRPService::class.java).client

        // 1. Create a new payment.
        val payment = xrpClient.createPayment(
                source = xrpClient.address,
                destination = (settlementInstructions as XRPSettlementInstructions).accountToPay,
                amount = obligation.faceAmount.toXRPAmount(),
                fee = DEFAULT_XRP_FEE,
                linearId = SecureHash.sha256(obligation.linearId.id.toString()).toXRPHash()
        )

        // 2. Sign and submit the payment.
        val signedPayment = xrpClient.signPayment(payment)
        val paymentResponse = xrpClient.submitTransaction(signedPayment)

        // 3. Return the payment hash.
        return paymentResponse.txJson.hash
    }

    override fun checkPaymentNotAlreadyMade(obligation: Obligation.State<*>) {
        // Check that the payment hasn't already been made.
        // TODO: More work needs to be done to make this flow idempotent.
        check(settlementInstructions.paymentReference == null) {
            "A ripple payment payment has already been made."
        }
    }

}