package com.r3.corda.finance.obligation.oracle.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.httpGet
import com.r3.corda.finance.swift.services.SWIFTPaymentException
import com.r3.corda.finance.swift.services.SWIFTService
import com.r3.corda.finance.swift.types.SWIFTErrorResponse
import com.r3.corda.finance.swift.types.SWIFTPaymentStatus
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import java.io.File
import java.nio.file.Paths

@CordaService
class SwiftOracleService(private val appServiceHub : AppServiceHub) : SingletonSerializeAsToken() {
    private var _config = loadConfig()

    private fun apiUrl() = _config.getString("apiUrl")
            ?: throw IllegalArgumentException("apiUrl must be provided")

    private fun apiKey() = _config.getString("apiKey")
            ?: throw IllegalArgumentException("apiKey must be provided")


    /**
     * Attempts to load service configuration from cordapps/config with a fallback to classpath
     */
    private fun loadConfig() : Config {
        val fileName = "swift.conf"
        val defaultLocation = (Paths.get("cordapps").resolve("config").resolve(fileName)).toFile()
        return if (defaultLocation.exists()) ConfigFactory.parseFile(defaultLocation)
        else ConfigFactory.parseFile(File(SWIFTService::class.java.classLoader.getResource(fileName).toURI()))
    }


    /**
     * Fetches SWIFT payment status
     */
    fun getPaymentStatus(uetr: String) : SWIFTPaymentStatus {
        val checkStatusUrl = "${apiUrl()}/$uetr/tracker_status"

        SWIFTService.logger.info("Submitting payment status request. UETR=$uetr")
        val (req, res, result) = checkStatusUrl
                .httpGet()
                .header("x-api-key" to apiKey())
                .response()

        val responseData = String(res.data)
        val mapper = jacksonObjectMapper()
        if (res.httpStatusCode >= 400) {
            SWIFTService.logger.warn("Error during retrieving payment status. UETR=$uetr, SWIFT_HTTP_STATUS=${res.httpStatusCode}, SWIFT_HTTP_ERROR_RESPONSE=$responseData")
            val parsedResponse = mapper.readValue<SWIFTErrorResponse>(responseData)
            throw SWIFTPaymentException("Error during retrieving payment status request. " +
                    "Payment UETR=$uetr, " +
                    "HTTP status=${res.httpStatusCode}, " +
                    "HTTP response=$responseData", parsedResponse)
        } else {
            SWIFTService.logger.info("Successfully retrieved payment status. UETR=$uetr, SWIFT_HTTP_STATUS=${res.httpStatusCode}, SWIFT_HTTP_ERROR_RESPONSE=$responseData")
            return mapper.readValue(responseData)
        }
    }
}