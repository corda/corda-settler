package net.corda.finance.ripple

import com.ripple.core.coretypes.AccountID
import net.corda.finance.ripple.types.*
import net.corda.finance.ripple.utilities.makeRequest
import java.net.URI

/** An XRP client which can obtain account and transaction information for any account. */
interface ReadOnlyXRPClient {

    val nodeUri: URI

    fun accountInfo(accountId: AccountID): AccountInfoResponse {
        val accountInfoRequest = AccountInfoRequest(accountId.address)
        return makeRequest<AccountInfoResultObject>(nodeUri, "account_info", accountInfoRequest).result
    }

    fun transaction(hash: String): TransactionInfoResponse {
        val transactionInfoRequest = TransactionInfoRequest(hash)
        return makeRequest<TransactionInfoResponseObject>(nodeUri, "tx", transactionInfoRequest).result
    }

    fun serverState(): ServerStateResponse {
        return makeRequest<ServerStateResponseObject>(nodeUri, "server_state", Unit).result
    }

}
