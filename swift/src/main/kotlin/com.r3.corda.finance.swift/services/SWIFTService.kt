package com.r3.corda.finance.swift.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import com.r3.corda.finance.obligation.types.FiatCurrency
import com.r3.corda.finance.swift.types.SWIFTErrorResponse
import com.r3.corda.finance.swift.types.SWIFTInstructedPaymentAmount
import com.r3.corda.finance.swift.types.SWIFTParticipantAccount
import com.r3.corda.finance.swift.types.SWIFTParticipantAgent
import com.r3.corda.finance.swift.types.SWIFTParticipantInfo
import com.r3.corda.finance.swift.types.SWIFTParticipantOrganisationIdentification
import com.r3.corda.finance.swift.types.SWIFTPaymentAmount
import com.r3.corda.finance.swift.types.SWIFTPaymentIdentification
import com.r3.corda.finance.swift.types.SWIFTRequestedExecutionDate
import com.r3.corda.finance.swift.types.SWIFTPaymentResponse
import com.r3.corda.finance.swift.types.SWIFTPaymentStatus
import com.r3.corda.finance.swift.types.SwiftPaymentInstruction
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowException
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.IllegalArgumentException
import java.math.BigDecimal
import java.nio.file.Paths
import java.text.DecimalFormat
import java.util.*
import java.text.SimpleDateFormat



@CordaService
class SWIFTService(private val appServiceHub : AppServiceHub) : SingletonSerializeAsToken() {
    companion object {
        val logger = LoggerFactory.getLogger(SWIFTService::class.java)
    }

    private var _config = loadConfig()

    private fun settlementUrl() = _config.getString("settlementUrl")
            ?: throw IllegalArgumentException("settlementUrl must be provided")

    private fun creditorName() = _config.getString("creditorName")
            ?: throw IllegalArgumentException("creditorName must be provided")

    private fun creditorLei() = _config.getString("creditorLei")
            ?: throw IllegalArgumentException("creditorLei must be provided")

    private fun creditorIban() = _config.getString("creditorIban")
            ?: throw IllegalArgumentException("creditorIban must be provided")

    private fun creditorBicfi() = _config.getString("creditorBicfi")
            ?: throw IllegalArgumentException("creditorBicfi must be provided")

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
     * Submits a payment to the SWIFT gateway
     */
    fun makePayment(e2eId : String,
                    executionDate : Date,
                    amount : Amount<FiatCurrency>,
                    debtorName : String,
                    debtorLei : String,
                    debtorIban : String,
                    debtorBicfi : String,
                    remittanceInformation : String
    ) : SWIFTPaymentResponse {
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd")
        val amountFormatter = DecimalFormat("#0.##")
        // we need to convert amount from Corda representation before submitting to swift
        val doubleAmount = BigDecimal.valueOf(amount.quantity).div(amount.displayTokenSize.multiply(BigDecimal.valueOf(100)))

        // creating SWIFT payment instruction payload
        val swiftPaymentInstruction = SwiftPaymentInstruction(
                SWIFTPaymentIdentification(e2eId),
                SWIFTRequestedExecutionDate(dateFormatter.format(executionDate)),
                SWIFTPaymentAmount(SWIFTInstructedPaymentAmount(amount.token.symbol, amount = amountFormatter.format(doubleAmount))),
                SWIFTParticipantInfo(debtorName, SWIFTParticipantOrganisationIdentification(debtorLei)),
                SWIFTParticipantAgent(debtorBicfi),
                SWIFTParticipantAccount(debtorIban),
                SWIFTParticipantInfo(creditorName(), SWIFTParticipantOrganisationIdentification(creditorLei())),
                SWIFTParticipantAgent(creditorBicfi()),
                SWIFTParticipantAccount(creditorIban()),
                remittanceInformation)

        val mapper = jacksonObjectMapper()
        val paymentUrl = "${settlementUrl()}/payment_initiation"
        val paymentInstructionId = swiftPaymentInstruction.paymentIdentification.e2eIdentification

        logger.info("Submitting payment instruction $swiftPaymentInstruction to $paymentUrl. PAYMENT_INSTRUCTION_ID=$paymentInstructionId")

        // making HTTP request
        val (req, res, result) = paymentUrl
                .httpPost()
                .header("Content-Type" to "application/json")
                .body(mapper.writeValueAsString(swiftPaymentInstruction))
                .response()

        val responseData = String(res.data)
        // if the payment attempt resulted to error - logging and throwing FlowException
        if (res.httpStatusCode >= 400) {
            logger.warn("Error during submitting payment. PAYMENT_INSTRUCTION_ID=$paymentInstructionId, SWIFT_HTTP_STATUS=${res.httpStatusCode}, SWIFT_HTTP_ERROR_RESPONSE=$responseData")
            val parsedResponse = mapper.readValue<SWIFTErrorResponse>(responseData)
            throw SWIFTPaymentException("Error during submitting payment instruction. " +
                    "Payment instruction=$swiftPaymentInstruction, " +
                    "HTTP status=${res.httpStatusCode}, " +
                    "HTTP response=$responseData", parsedResponse)
        } else {
            logger.info("Successfully submitted payment instruction. PAYMENT_INSTRUCTION_ID=$paymentInstructionId, SWIFT_HTTP_STATUS=${res.httpStatusCode}, SWIFT_HTTP_ERROR_RESPONSE=$responseData")
            return mapper.readValue(responseData)
        }
    }

    /**
     * Fetches SWIFT payment status
     */
    fun checkPaymentStatus(uetr: String) : SWIFTPaymentStatus {
        val checkStatusUrl = "${settlementUrl()}/$uetr/tracker_status"

        logger.info("Submitting payment status request. UETR=$uetr")
        val (req, res, result) = checkStatusUrl.httpGet().response()

        val responseData = String(res.data)
        val mapper = jacksonObjectMapper()
        if (res.httpStatusCode >= 400) {
            logger.warn("Error during retrieving payment status. UETR=$uetr, SWIFT_HTTP_STATUS=${res.httpStatusCode}, SWIFT_HTTP_ERROR_RESPONSE=$responseData")
            val parsedResponse = mapper.readValue<SWIFTErrorResponse>(responseData)
            throw SWIFTPaymentException("Error during retrieving payment status request. " +
                    "Payment UETR=$uetr, " +
                    "HTTP status=${res.httpStatusCode}, " +
                    "HTTP response=$responseData", parsedResponse)
        } else {
            logger.info("Successfully retrieved payment status. UETR=$uetr, SWIFT_HTTP_STATUS=${res.httpStatusCode}, SWIFT_HTTP_ERROR_RESPONSE=$responseData")
            return mapper.readValue(responseData)
        }
    }

}

class SWIFTPaymentException(message : String, val errorResponse : SWIFTErrorResponse) : FlowException(message)