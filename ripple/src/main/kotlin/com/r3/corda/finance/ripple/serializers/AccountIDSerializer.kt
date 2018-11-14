package com.r3.corda.finance.ripple.serializers

import com.ripple.core.coretypes.AccountID
import net.corda.core.serialization.SerializationCustomSerializer

class AccountIDSerializer : SerializationCustomSerializer<AccountID, AccountIDSerializer.Proxy> {
    data class Proxy(val address: String)

    override fun toProxy(obj: AccountID) = Proxy(obj.address)

    override fun fromProxy(proxy: Proxy): AccountID {
        return AccountID.fromString(proxy.address)
    }
}