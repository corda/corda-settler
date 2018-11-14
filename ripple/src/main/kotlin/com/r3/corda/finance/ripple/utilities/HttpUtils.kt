package com.r3.corda.finance.ripple.utilities

import com.r3.corda.finance.ripple.types.RequestObject
import com.r3.corda.finance.ripple.types.ResultObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

fun URL.openHttpConnection(): HttpURLConnection = openConnection() as HttpURLConnection

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

inline fun <reified T : ResultObject> deserialize(response: String): T {
    return mapper.readValue(response, T::class.java)
}

open class RippleRpcException : Exception()

class AccountNotFoundException : RippleRpcException() {
    override fun toString() = "Account ID not found."
}

class TransactionNotFoundException : RippleRpcException() {
    override fun toString() = "Transaction ID not found."
}

class IncorrectSequenceNumberException : RippleRpcException() {
    override fun toString() = "The sequence number is incorrect. " +
            "It is likely that the same transaction has been submitted twice."
}

class AlreadysubmittedException : RippleRpcException() {
    override fun toString() = "The transaction has already been submitted."
}
