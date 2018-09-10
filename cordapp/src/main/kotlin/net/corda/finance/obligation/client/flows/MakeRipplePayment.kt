package net.corda.finance.obligation.client.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.SecureHash
import net.corda.core.transactions.SignedTransaction
import net.corda.finance.obligation.contracts.Obligation
import net.corda.finance.obligation.flows.MakeOffLedgerPayment
import net.corda.finance.obligation.flows.MakeOffLedgerPayment.Companion.CHECKING_BALANCE
import net.corda.finance.obligation.flows.MakeOffLedgerPayment.Companion.MAKING_PAYMENT
import net.corda.finance.ripple.services.Ripple
import net.corda.finance.ripple.types.RippleSettlementInstructions
import net.corda.finance.ripple.types.SubmitPaymentResponse
import net.corda.finance.ripple.utilities.toRippleAmount
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
class MakeRipplePayment(obligationStateAndRef: StateAndRef<Obligation.State<*>>) : MakeOffLedgerPayment(obligationStateAndRef) {

    private val rippleClient = serviceHub.cordaService(Ripple::class.java).client

    override fun checkBalance(requiredAmount: Amount<*>) {
        val ourAccountInfo = rippleClient.accountInfo(rippleClient.address)
        val balance = ourAccountInfo.accountData.balance
        check(balance > requiredAmount.toRippleAmount()) {
            "You do not have enough XRP to make the payment."
        }
    }

    // We don't want to serialise any of this stuff.
    override fun makePayment(obligation: Obligation.State<*>): SubmitPaymentResponse {
        val settlementInstructions = obligation.settlementInstructions!! as RippleSettlementInstructions

        // 1. Create a new payment.
        val payment = rippleClient.createPayment(
                source = rippleClient.address,
                destination = account,
                amount = obligation.amount.toRippleAmount(),
                fee = DEFAULT_RIPPLE_FEE,
                linearId = SecureHash.sha256(obligation.linearId.id.toString()).toRippleHash()
        )

        // 2. Sign and submit the payment.
        val signedPayment = rippleClient.signPayment(payment)

        logger.info("Serialised Ripple payment: ${payment.toJSON()}")
        logger.info("Serialised Ripple signed transaction: ${signedPayment.tx_blob}")

        return rippleClient.submitTransaction(signedPayment)
    }

    @Suspendable
    override fun call(): SignedTransaction {


        // 2. Get settlement instructions.
        val settlementInstructions = obligation.settlementInstructions as RippleSettlementInstructions
        val accountToPay = settlementInstructions.accountToPay

        // Check that the specified account has enough XRP to pay.
        progressTracker.currentStep = CHECKING_BALANCE
        checkBalance(obligation.amount)

        // 3. Check that the payment hasn't already been made.
        check(settlementInstructions.rippleTransactionHash == null) {
            "A ripple payment payment has already been made."
        }

        // 4. Make payment.
        progressTracker.currentStep = MAKING_PAYMENT
        val response = makePayment(accountToPay, obligation)

        // 5. Add payment hash to settlement instructions.
        val paymentHash = SecureHash.parse(response.txJson.hash)
        val updatedSettlementInstructions = settlementInstructions.addRippleTransactionHash(paymentHash)
        val obligationWithUpdatedSettlementInstructions = obligation.withSettlementTerms(updatedSettlementInstructions)

    }

}