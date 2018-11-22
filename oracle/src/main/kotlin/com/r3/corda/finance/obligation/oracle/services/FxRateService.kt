package com.r3.corda.finance.obligation.oracle.services

import com.r3.corda.finance.obligation.Money
import com.typesafe.config.ConfigFactory
import net.corda.core.internal.openHttpConnection
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import java.net.URI

@CordaService
class FxRateService(val services: AppServiceHub) : SingletonSerializeAsToken() {

    private val apiUrl = "https://min-api.cryptocompare.com/data/price"

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

    fun createRequestUrl(base: Money, foreign: Money) {
        val queryString = "$apiUrl?fsym=${base.symbol}&tsyms=${foreign.symbol}&api_key=$apiKey"
        val resposne = makeRequest(URI(queryString))
    }

}

