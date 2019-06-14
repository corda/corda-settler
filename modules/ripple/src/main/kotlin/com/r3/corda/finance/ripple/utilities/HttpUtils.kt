package com.r3.corda.finance.ripple.utilities

import com.r3.corda.finance.ripple.types.RequestObject
import net.corda.core.internal.openHttpConnection
import java.io.OutputStreamWriter
import java.net.URI

fun jsonRpcRequest(uri: URI, requestBody: String): String {
    return uri.toURL().openHttpConnection().run {
        doInput = true
        doOutput = true
        requestMethod = "POST"
        setRequestProperty("Content-Type", "application/json")
        OutputStreamWriter(outputStream).use { out -> out.write(requestBody) }
        inputStream.reader().readText()
    }
}

fun makeRequest(uri: URI, requestType: String, request: Any): String {
    val requestObject = RequestObject(requestType, listOf(request))
    val serializedRequest = mapper.writeValueAsString(requestObject)
    return jsonRpcRequest(uri, serializedRequest)
}


