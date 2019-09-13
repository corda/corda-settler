package com.r3.corda.lib.settler.contracts.serialization

import net.corda.core.contracts.TokenizableAssetInfo
import net.corda.core.serialization.SerializationWhitelist

class SerializationWhitelist : SerializationWhitelist {
    override val whitelist = listOf(
            TokenizableAssetInfo::class.java
    )
}