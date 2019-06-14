package com.r3.corda.finance.ripple.services

import com.r3.corda.finance.ripple.types.*
import com.r3.corda.finance.ripple.utilities.deserialize
import com.r3.corda.finance.ripple.utilities.makeRequest
import com.ripple.core.coretypes.AccountID
import com.ripple.core.coretypes.Amount
import com.ripple.core.coretypes.hash.Hash256
import com.ripple.core.coretypes.uint.UInt32
import com.ripple.core.types.known.tx.signed.SignedTransaction
import com.ripple.core.types.known.tx.txns.Payment

/**
 * An XRP client which can create and sign payment transactions for a specified account.
 */
interface ReadWriteXRPClient : ReadOnlyXRPClient {

    val secret: String
    val address: AccountID

    fun submitTransaction(signedTransaction: SignedTransaction): SubmitPaymentResponse {
        val submitPaymentRequest = SubmitPaymentRequest(signedTransaction.tx_blob)
        val response = makeRequest(nodeUri, "submit", submitPaymentRequest)
        val deserializedResponse = deserialize<SubmitPaymentResultObject>(response).result
        return when (deserializedResponse.engineResult) {
            "tecUNFUNDED_PAYMENT" -> throw InsufficientBalanceException()
            "tefPAST_SEQ", "terPRE_SEQ" -> throw IncorrectSequenceNumberException()
            "tefALREADY" -> throw IncorrectSequenceNumberException()
            "temREDUNDANT" -> throw PaymentToSelfException()
            "tesSUCCESS" -> deserializedResponse
            else -> throw IllegalStateException("Unhandled error: $deserializedResponse")
        }
    }

    fun nextSequenceNumber(accountId: AccountID): UInt32 = UInt32(accountInfo(accountId).accountData.sequence)

    fun signPayment(payment: Payment): SignedTransaction = payment.sign(secret)

    fun createPayment(sequence: UInt32, source: AccountID, destination: AccountID, amount: Amount, fee: Amount, linearId: Hash256): Payment {
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