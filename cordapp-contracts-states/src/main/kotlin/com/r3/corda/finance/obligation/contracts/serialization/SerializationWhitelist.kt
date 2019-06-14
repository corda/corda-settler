package com.r3.corda.finance.obligation.contracts.serialization

import net.corda.core.contracts.TokenizableAssetInfo
import net.corda.core.serialization.SerializationWhitelist

class SerializationWhitelist : SerializationWhitelist {
    override val whitelist = listOf(
            TokenizableAssetInfo::class.java,
            Number::class.java
    )
}