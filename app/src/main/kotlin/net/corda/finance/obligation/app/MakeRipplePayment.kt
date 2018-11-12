package net.corda.finance.obligation.app

import net.corda.core.crypto.SecureHash
import net.corda.finance.obligation.types.PaymentReference
import net.corda.finance.ripple.XRPClientForPayment
import net.corda.finance.ripple.types.XRPSettlementInstructions
import net.corda.finance.ripple.utilities.DEFAULT_XRP_FEE
import net.corda.finance.ripple.utilities.toXRPAmount
import net.corda.finance.ripple.utilities.toXRPHash

/*

For testing...

Address     ra6mzL1Xy9aN5eRdjzn9CHTMwcczG1uMpN
Secret      sasKgJbTbka3ahFew2BZybfNg494C
Balance     10,000 XRP

Address     rNmkj4AtjEHJh3D9hMRC4rS3CXQ9mX4S4b
Secret      ssn8cYYksFFexYq97sw9UnvLnMKgh
Balance     10,000 XRP

Address
r3fRJgNRDefP71puX9iXrTZgJkFs4gTSex
Secret
sn8oipr5yV9PqAwYtgJwHRraq9cY1
Balance
10,000 XRP

 */

fun checkBalance(rippleClient: XRPClientForPayment, obligationModel: ObligationModel<*>): Boolean {
    // Check the balance on the supplied XRPService address.
    val ourAccountInfo = rippleClient.accountInfo(rippleClient.address)
    val requiredAmount = obligationModel.amount
    val balance = ourAccountInfo.accountData.balance
    println("$balance vs $requiredAmount (${requiredAmount.toXRPAmount()})")
    return balance > requiredAmount.toXRPAmount()
}

fun makePayment(rippleClient: XRPClientForPayment, obligationModel: ObligationModel<*>): PaymentReference {
    // 1. Create a new payment.
    val payment = rippleClient.createPayment(
            source = rippleClient.address,
            destination = (obligationModel.settlementInstructions as XRPSettlementInstructions).accountToPay,
            amount = obligationModel.amount.toXRPAmount(),
            fee = DEFAULT_XRP_FEE,
            linearId = SecureHash.sha256(obligationModel.linearId.id.toString()).toXRPHash()
    )

    // 2. Sign and submit the payment.
    val signedPayment = rippleClient.signPayment(payment)
    val paymentResponse = rippleClient.submitTransaction(signedPayment)

    // 3. Return the payment hash.
    return paymentResponse.txJson.hash
}
