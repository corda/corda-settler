package net.corda.finance.ripple

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.ripple.core.coretypes.AccountID
import com.ripple.core.coretypes.Amount
import com.ripple.core.coretypes.uint.UInt32
import com.ripple.core.types.known.tx.signed.SignedTransaction
import com.ripple.core.types.known.tx.txns.Payment
import net.corda.finance.obligation.jsonRpcRequest
import net.corda.finance.obligation.mapper
import java.net.URI

class RippleClient(val nodeUri: URI, val secret: String) {

    data class RequestObject(val method: String, val params: List<Any>)

    data class AccountInfoResultObject(val result: AccountInfoResponse)

    data class AccountInfoRequest(
            @JsonProperty("account")
            val account: String,

            @JsonProperty("strict")
            val strict: Boolean = true,

            @JsonProperty("queue")
            val queue: Boolean = false,

            @JsonProperty("ledger_index")
            val ledgerIndex: String = "current"
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AccountInfoResponse(
            @JsonProperty("account_data")
            val accountData: AccountData,

            @JsonProperty("ledger_current_index")
            val ledgerCurrentIndex: Int,

            @JsonProperty("status")
            val status: String,

            @JsonProperty("validated")
            val validated: Boolean
    )

    data class AccountData(
            @JsonProperty("Account")
            val account: AccountID,

            @JsonProperty("Balance")
            val balance: Amount,

            @JsonProperty("Flags")
            val flags: String,

            @JsonProperty("LedgerEntryType")
            val ledgerEntryType: String,

            @JsonProperty("OwnerCount")
            val ownerCount: String,

            @JsonProperty("PreviousTxnID")
            val previousTxnID: String,

            @JsonProperty("PreviousTxnLgrSeq")
            val previousTxnLgrSeq: String,

            @JsonProperty("Sequence")
            val sequence: String,

            @JsonProperty("index")
            val index: String
    )

    data class SubmitPaymentRequest(
            @JsonProperty("tx_blob")
            val txBlob: String
    )

    data class SubmitPaymentResultObject(val result: SubmitPaymentResponse)

    data class SubmitPaymentResponse(
            @JsonProperty("engine_result")
            val engineResult: String,

            @JsonProperty("engine_result_code")
            val engineResultCode: String,

            @JsonProperty("engine_result_message")
            val engineResultMessage: String,

            @JsonProperty("status")
            val status: String,

            @JsonProperty("tx_blob")
            val txBlob: String,

            @JsonProperty("tx_json")
            val txJson: TransactionJson
    )

    data class TransactionAmount(
            val currency: String,
            val issuer: String,
            val value: String
    )

    data class TransactionJson(
            val Account: String,
            val Amount: String,
            val Destination: String,
            val Fee: String,
            val Flags: String,
            val Sequence: String,
            val SigningPubKey: String,
            val TransactionType: String,
            val TxnSignature: String,
            val hash: String
    )

    fun getAccountData(accountId: AccountID): AccountInfoResponse {
        val accountInfoRequest = AccountInfoRequest(accountId.address)
        val requestObject = RequestObject("account_info", listOf(accountInfoRequest))
        val request = mapper.writeValueAsString(requestObject)
        val result = jsonRpcRequest(nodeUri, request)
        return mapper.readValue(result, AccountInfoResultObject::class.java).result
    }

    fun getSequenceNumberForAccount(accountId: AccountID): UInt32 = UInt32(getAccountData(accountId).accountData.sequence)

    fun signPayment(payment: Payment): SignedTransaction = payment.sign(secret)

    fun createPayment(source: AccountID, destination: AccountID, amount: Amount, fee: Amount): Payment {
        val sequence = getSequenceNumberForAccount(source)
        return Payment().apply {
            account(source)
            destination(destination)
            amount(amount)
            sequence(sequence)
            fee(fee)
        }
    }

    fun submitPayment(signedTransaction: SignedTransaction): SubmitPaymentResponse {
        val submitPaymentRequest = SubmitPaymentRequest(signedTransaction.tx_blob)
        val requestObject = RequestObject("submit", listOf(submitPaymentRequest))
        val request = mapper.writeValueAsString(requestObject)
        val result = jsonRpcRequest(nodeUri, request)
        return mapper.readValue(result, SubmitPaymentResultObject::class.java).result
    }

}

fun main(args: Array<String>) {
    val nodeUri = URI("http://s.altnet.rippletest.net:51234")
    val client = RippleClient(nodeUri, "ssn8cYYksFFexYq97sw9UnvLnMKgh")
    val result = client.getAccountData(AccountID.fromString("rNmkj4AtjEHJh3D9hMRC4rS3CXQ9mX4S4b"))
    println(result)

    val payment = client.createPayment(
            source = AccountID.fromString("rNmkj4AtjEHJh3D9hMRC4rS3CXQ9mX4S4b"),
            destination = AccountID.fromString("ra6mzL1Xy9aN5eRdjzn9CHTMwcczG1uMpN"),
            amount = Amount.fromString("1000000"),
            fee = Amount.fromString("1000")
    )

    val signedPayment = client.signPayment(payment)
    val response = client.submitPayment(signedPayment)
    println(response)

}
