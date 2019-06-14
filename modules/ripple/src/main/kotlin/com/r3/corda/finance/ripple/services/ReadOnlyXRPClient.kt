package com.r3.corda.finance.ripple.services

import com.r3.corda.finance.ripple.types.*
import com.r3.corda.finance.ripple.utilities.deserialize
import com.r3.corda.finance.ripple.utilities.makeRequest
import com.r3.corda.finance.ripple.utilities.mapper
import com.ripple.core.coretypes.AccountID
import java.net.URI

/** An XRP client which can obtain account and transaction information for any account. */
interface ReadOnlyXRPClient {

    val nodeUri: URI

    fun <T : RippleRpcException> handleErrors(response: String, map: Map<Int, Class<T>>) {
        val jsonObject = mapper.readTree(response)
        val resultNode = jsonObject.get("result")
        if (resultNode.has("error")) {
            val errorCode = resultNode.get("error_code").asInt()
            if (errorCode in map.keys) {
                map[errorCode]?.let { throw it.newInstance() }
            }
        }
    }

    fun accountInfo(accountId: AccountID): AccountInfoResponse {
        val accountInfoRequest = AccountInfoRequest(accountId.address)
        val response = makeRequest(nodeUri, "account_info", accountInfoRequest)
        handleErrors(response, mapOf(19 to AccountNotFoundException::class.java))
        return deserialize<AccountInfoResultObject>(response).result
    }

    fun transaction(hash: String): TransactionInfoResponse {
        val transactionInfoRequest = TransactionInfoRequest(hash)
        val response = makeRequest(nodeUri, "tx", transactionInfoRequest)
        handleErrors(response, mapOf(
                29 to TransactionNotFoundException::class.java,
                72 to TransactionNotFoundException::class.java
        ))
        return deserialize<TransactionInfoResponseObject>(response).result
    }

    fun serverState(): ServerStateResponse {
        val response = makeRequest(nodeUri, "server_state", Unit)
        return deserialize<ServerStateResponseObject>(response).result
    }

    fun ledgerIndex(): LedgerCurrentIndexResponse {
        val response = makeRequest(nodeUri, "ledger_current", Unit)
        return deserialize<LedgerCurrentIndexResponseObject>(response).result
    }

}
