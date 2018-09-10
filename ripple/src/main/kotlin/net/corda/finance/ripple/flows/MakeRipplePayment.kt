package net.corda.finance.ripple.flows

import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.SecureHash
import net.corda.finance.obligation.contracts.Obligation
import net.corda.finance.obligation.flows.MakeOffLedgerPayment
import net.corda.finance.obligation.types.OffLedgerSettlementInstructions
import net.corda.finance.obligation.types.PaymentReference
import net.corda.finance.ripple.services.RippleService
import net.corda.finance.ripple.types.RippleSettlementInstructions
import net.corda.finance.ripple.utilities.DEFAULT_RIPPLE_FEE
import net.corda.finance.ripple.utilities.toRippleAmount
import net.corda.finance.ripple.utilities.toRippleHash
import com.ripple.core.coretypes.Amount as RippleAmount

/*

For testing...

Address     ra6mzL1Xy9aN5eRdjzn9CHTMwcczG1uMpN
Secret      sasKgJbTbka3ahFew2BZybfNg494C
Balance     10,000 XRP

Address     rNmkj4AtjEHJh3D9hMRC4rS3CXQ9mX4S4b
Secret      ssn8cYYksFFexYq97sw9UnvLnMKgh
Balance     10,000 XRP
 */
class MakeRipplePayment(
        obligationStateAndRef: StateAndRef<Obligation.State<*>>,
        override val settlementInstructions: OffLedgerSettlementInstructions<*>
) : MakeOffLedgerPayment(obligationStateAndRef, settlementInstructions) {

    override fun checkBalance(requiredAmount: Amount<*>) {
        // Get a RippleService client.
        val rippleClient = serviceHub.cordaService(RippleService::class.java).client

        // Check the balance on the supplied RippleService address.
        val ourAccountInfo = rippleClient.accountInfo(rippleClient.address)
        val balance = ourAccountInfo.accountData.balance
        check(balance > requiredAmount.toRippleAmount()) {
            "You do not have enough XRP to make the payment."
        }
    }

    // We don't want to serialise any of this stuff not @Suspendable.
    override fun makePayment(obligation: Obligation.State<*>): PaymentReference {
        // Get a RippleService client.
        val rippleClient = serviceHub.cordaService(RippleService::class.java).client

        // 1. Create a new payment.
        val payment = rippleClient.createPayment(
                source = rippleClient.address,
                destination = (settlementInstructions as RippleSettlementInstructions).accountToPay,
                amount = obligation.amount.toRippleAmount(),
                fee = DEFAULT_RIPPLE_FEE,
                linearId = SecureHash.sha256(obligation.linearId.id.toString()).toRippleHash()
        )

        // 2. Sign and submit the payment.
        val signedPayment = rippleClient.signPayment(payment)
        val paymentResponse = rippleClient.submitTransaction(signedPayment)

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