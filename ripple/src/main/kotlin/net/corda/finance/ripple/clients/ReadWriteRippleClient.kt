package net.corda.finance.ripple.clients

import com.ripple.core.coretypes.AccountID
import com.ripple.core.coretypes.Amount
import com.ripple.core.coretypes.hash.Hash256
import com.ripple.core.coretypes.uint.UInt32
import com.ripple.core.types.known.tx.signed.SignedTransaction
import com.ripple.core.types.known.tx.txns.Payment
import net.corda.finance.ripple.types.SubmitPaymentRequest
import net.corda.finance.ripple.types.SubmitPaymentResponse
import net.corda.finance.ripple.types.SubmitPaymentResultObject
import net.corda.finance.ripple.utilities.makeRequest

interface ReadWriteRippleClient : ReadOnlyRippleClient {

    val secret: String
    val address: AccountID

    fun submitTransaction(signedTransaction: SignedTransaction): SubmitPaymentResponse {
        val submitPaymentRequest = SubmitPaymentRequest(signedTransaction.tx_blob)
        return makeRequest<SubmitPaymentResultObject>(nodeUri, "submit", submitPaymentRequest).result
    }

    fun nextSequenceNumber(accountId: AccountID): UInt32 = UInt32(accountInfo(accountId).accountData.sequence)

    fun signPayment(payment: Payment): SignedTransaction = payment.sign(secret)

    fun createPayment(source: AccountID, destination: AccountID, amount: Amount, fee: Amount, linearId: Hash256): Payment {
        val sequence = nextSequenceNumber(source)
        return Payment().apply {
            account(source)
            destination(destination)
            amount(amount)
            sequence(sequence)
            fee(fee)
            invoiceID(linearId)
        }
    }

}