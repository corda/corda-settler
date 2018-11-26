package com.r3.corda.finance.obligation.oracle.services

import com.fasterxml.jackson.databind.JsonNode
import com.r3.corda.finance.obligation.GBP
import com.r3.corda.finance.obligation.USD
import com.r3.corda.finance.obligation.types.FxRateRequest
import com.r3.corda.finance.obligation.types.FxRateResponse
import com.r3.corda.finance.obligation.types.Money
import com.r3.corda.finance.ripple.utilities.XRP
import com.r3.corda.finance.ripple.utilities.mapper
import com.typesafe.config.ConfigFactory
import net.corda.core.internal.openHttpConnection
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import java.net.URI
import java.time.Instant
import javax.json.JsonObject

@CordaService
class FxRateService(val services: AppServiceHub) : SingletonSerializeAsToken() {

    private val apiUrl = "https://min-api.cryptocompare.com/data/pricehistorical"
    private val configFileName: String = "fx.conf"
    private val apiKey: String by lazy { ConfigFactory.parseResources(configFileName).getString("apiKey") }

    private fun makeRequest(uri: URI): String {
        return uri.toURL().openHttpConnection().run {
            doInput = true
            doOutput = false
            requestMethod = "GET"
            inputStream.reader().readText()
        }
    }

    private fun createRequestUrl(base: Money, foreign: Money, timestamp: Long): String {
        return "$apiUrl?" +
                "fsym=${base.symbol}" +
                "&tsyms=${foreign.symbol}" +
                "&api_key=$apiKey" +
                "&ts=$timestamp" +
                "&calculationType=MidHighLow"
    }

    private fun checkForErrors(jsonObject: JsonNode) {
        if (jsonObject.has("Response")) {
            if (jsonObject.get("Response").asText() == "Error") {
                val message = jsonObject.get("Message").asText()
                throw IllegalArgumentException(message)
            }
        }
    }

    private fun parseResponse(response: String, request: FxRateRequest): Double {
        val jsonObject: JsonNode = mapper.readTree(response)
        checkForErrors(jsonObject)
        val base = request.baseCurrency.symbol
        val baseCurrencyNode = jsonObject.get(base)
        val counter = request.counterCurrency.symbol
        return baseCurrencyNode.get(counter).asDouble()
    }

    fun getRate(request: FxRateRequest): FxRateResponse {
        val url = createRequestUrl(request.baseCurrency, request.counterCurrency, request.time.toEpochMilli())
        val response = makeRequest(URI(url))
        val rate = parseResponse(response, request)
        return FxRateResponse(request.baseCurrency, request.counterCurrency, request.time, rate)
    }

}