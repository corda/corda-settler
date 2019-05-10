package com.r3.corda.finance.ripple.types

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.ripple.core.coretypes.AccountID
import com.ripple.core.coretypes.Amount

data class RequestObject(val method: String, val params: List<Any>)

interface ResultObject {
    val result: Any
}

/** Account information. */

data class AccountInfoResultObject(@JsonProperty("result") override val result: AccountInfoResponse) : ResultObject

data class AccountInfoRequest(
        @JsonProperty("account") val account: String,
        @JsonProperty("strict") val strict: Boolean = true,
        @JsonProperty("queue") val queue: Boolean = false,
        @JsonProperty("ledger_index") val ledgerIndex: String = "current"
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AccountInfoResponse(
        @JsonProperty("account_data") val accountData: AccountData,
        @JsonProperty("ledger_current_index") val ledgerCurrentIndex: Int,
        @JsonProperty("status") val status: String,
        @JsonProperty("validated") val validated: Boolean
)

data class AccountData(
        @JsonProperty("Account") val account: AccountID,
        @JsonProperty("Balance") val balance: Amount,
        @JsonProperty("Flags") val flags: String,
        @JsonProperty("LedgerEntryType") val ledgerEntryType: String,
        @JsonProperty("OwnerCount") val ownerCount: String,
        @JsonProperty("PreviousTxnID") val previousTxnID: String,
        @JsonProperty("PreviousTxnLgrSeq") val previousTxnLgrSeq: String,
        @JsonProperty("Sequence") val sequence: String,
        @JsonProperty("index") val index: String
)

/** Submit transaction. */

data class SubmitPaymentRequest(@JsonProperty("tx_blob") val txBlob: String)

data class SubmitPaymentResultObject(@JsonProperty("result") override val result: SubmitPaymentResponse) : ResultObject

@JsonIgnoreProperties(ignoreUnknown = true)
data class SubmitPaymentResponse(
        @JsonProperty("engine_result") val engineResult: String,
        @JsonProperty("engine_result_code") val engineResultCode: String,
        @JsonProperty("engine_result_message") val engineResultMessage: String,
        @JsonProperty("status") val status: String,
        @JsonProperty("tx_blob") val txBlob: String,
        @JsonProperty("tx_json") val txJson: TransactionJson
)

@JsonIgnoreProperties(ignoreUnknown = true)
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

/** Transaction information. */

data class TransactionInfoRequest(
        @JsonProperty("transaction") val transaction: String,
        @JsonProperty("binary") val binary: Boolean = false
)

data class TransactionInfoResponseObject(@JsonProperty("result") override val result: TransactionInfoResponse) : ResultObject

@JsonIgnoreProperties(ignoreUnknown = true)
data class TransactionInfoResponse(
        @JsonProperty("Account") val account: AccountID,
        @JsonProperty("Amount") val amount: Amount,
        @JsonProperty("Destination") val destination: AccountID,
        @JsonProperty("Fee") val fee: Amount,
        @JsonProperty("Flags") val flags: String,
        @JsonProperty("InvoiceID") val invoiceId: String,
        @JsonProperty("Sequence") val sequence: Int,
        @JsonProperty("SigningPubKey") val signingPubKey: String,
        @JsonProperty("TransactionType") val transactionType: String,
        @JsonProperty("TxnSignature") val txnSignature: String,
        @JsonProperty("hash") val hash: String,
        @JsonProperty("inLedger") val inLedger: Int,
        @JsonProperty("ledger_index") val ledgerIndex: Int,
        @JsonProperty("meta") val meta: Meta,
        @JsonProperty("status") val status: String,
        @JsonProperty("validated") val validated: Boolean
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Meta(@JsonProperty("delivered_amount") val deliveredAmount: Amount)

/** Server settlementStatus. */

data class ServerStateResponseObject(@JsonProperty("result") override val result: ServerStateResponse) : ResultObject

data class ServerStateResponse(val state: ServerState, val status: String) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ServerState(
            @JsonProperty("server_state") val serverState: String,
            @JsonProperty("complete_ledgers") val completeLedgers: String
    )
}

/** Ledger current. */

data class LedgerCurrentIndexResponseObject(@JsonProperty("result") override val result: LedgerCurrentIndexResponse) : ResultObject

data class LedgerCurrentIndexResponse(
        @JsonProperty("ledger_current_index") val ledgerCurrentIndex: Long,
        @JsonProperty("status") val status: String
)