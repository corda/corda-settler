package com.r3.corda.finance.ripple.serializers

import net.corda.core.serialization.SerializationCustomSerializer
import java.net.URI

class URISerializer : SerializationCustomSerializer<URI, URISerializer.Proxy> {
    data class Proxy(val uri: String)

    override fun toProxy(obj: URI) = Proxy(obj.toString())

    override fun fromProxy(proxy: Proxy): URI {
        return URI(proxy.uri)
    }
}