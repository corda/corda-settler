package net.corda.finance.ripple.utilities

import net.corda.finance.ripple.types.RequestObject
import net.corda.finance.ripple.types.ResultObject
import java.io.IOException
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

fun URL.openHttpConnection(): HttpURLConnection = openConnection() as HttpURLConnection

val HttpURLConnection.errorMessage: String?
    get() {
        return errorStream?.let { inputStream ->
            inputStream.use { it.reader().readText() }
        }
    }

fun HttpURLConnection.checkOkResponse() {
    if (responseCode != HttpURLConnection.HTTP_OK) {
        throw IOException("Response Code $responseCode: $errorMessage")
    }
}

fun jsonRpcRequest(uri: URI, requestBody: String): String {
    return uri.toURL().openHttpConnection().run {
        doInput = true
        doOutput = true
        requestMethod = "POST"
        setRequestProperty("Content-Type", "application/json")
        OutputStreamWriter(outputStream).use { out -> out.write(requestBody) }
        checkOkResponse()
        inputStream.reader().readText()
    }
}

inline fun <reified T : ResultObject> makeRequest(uri: URI, requestType: String, request: Any): T {
    val requestObject = RequestObject(requestType, listOf(request))
    val serializedRequest = mapper.writeValueAsString(requestObject)
    val response = jsonRpcRequest(uri, serializedRequest)
    // TODO: hack to catch any error responses.
    if (response.contains("error")) {
        throw IllegalStateException("Request $requestType returned an error.")
    }
    return mapper.readValue(response, T::class.java)
}