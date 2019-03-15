package com.r3.corda.finance.swift.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import com.r3.corda.finance.obligation.types.FiatCurrency
import com.r3.corda.finance.swift.types.SWIFTInstructedPaymentAmount
import com.r3.corda.finance.swift.types.SWIFTParticipantAccount
import com.r3.corda.finance.swift.types.SWIFTParticipantAgent
import com.r3.corda.finance.swift.types.SWIFTParticipantInfo
import com.r3.corda.finance.swift.types.SWIFTParticipantOrganisationIdentification
import com.r3.corda.finance.swift.types.SWIFTPaymentAmount
import com.r3.corda.finance.swift.types.SWIFTPaymentIdentification
import com.r3.corda.finance.swift.types.SWIFTPaymentResponse
import com.r3.corda.finance.swift.types.SWIFTPaymentStatus
import com.r3.corda.finance.swift.types.SWIFTPaymentStatusType
import com.r3.corda.finance.swift.types.SWIFTRequestedExecutionDate
import com.r3.corda.finance.swift.types.SwiftPaymentInstruction
import net.corda.core.contracts.Amount
import net.corda.core.crypto.Crypto
import net.corda.core.flows.FlowException
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.security.PrivateKey
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

class SWIFTClient(
        private val apiUrl : String,
        private val apiKey : String,
        private val privateKey : PrivateKey) {
    companion object {
        private val logger = LoggerFactory.getLogger(SWIFTClient::class.java)!!
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
                    creditorName : String,
                    creditorLei : String,
                    creditorIban : String,
                    creditorBicfi : String,
                    remittanceInformation : String
    ) : SWIFTPaymentResponse {

        val paymentResponse = submitPaymentInstruction(e2eId,
                executionDate,
                amount,
                debtorName,
                debtorLei,
                debtorIban,
                debtorBicfi,
                creditorName,
                creditorLei,
                creditorIban,
                creditorBicfi,
                remittanceInformation)

        val unsignedPayload = getUnsignedPayload(paymentResponse.uetr)
        val signedPayloadBase64 = Base64.getEncoder().encodeToString(Crypto.doSign(privateKey, unsignedPayload))
        submitSignedPayload(paymentResponse.uetr, signedPayloadBase64)
        return paymentResponse
    }

    private fun submitPaymentInstruction(e2eId : String,
                                         executionDate : Date,
                                         amount : Amount<FiatCurrency>,
                                         debtorName : String,
                                         debtorLei : String,
                                         debtorIban : String,
                                         debtorBicfi : String,
                                         creditorName : String,
                                         creditorLei : String,
                                         creditorIban : String,
                                         creditorBicfi : String,
                                         remittanceInformation : String
    ) : SWIFTPaymentResponse {
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd")
        val amountFormatter = DecimalFormat("#0.##")
        // we need to convert amount from Corda representation before submitting to swift
        val doubleAmount = BigDecimal.valueOf(amount.quantity).multiply(amount.displayTokenSize)

        // creating SWIFT payment instruction payload
        val swiftPaymentInstruction = SwiftPaymentInstruction(
                SWIFTPaymentIdentification(e2eId),
                SWIFTRequestedExecutionDate(dateFormatter.format(executionDate)),
                SWIFTPaymentAmount(SWIFTInstructedPaymentAmount(amount.token.currencyCode, amount = amountFormatter.format(doubleAmount))),
                SWIFTParticipantInfo(debtorName, SWIFTParticipantOrganisationIdentification(debtorLei)),
                SWIFTParticipantAgent(debtorBicfi),
                SWIFTParticipantAccount(debtorIban),
                SWIFTParticipantInfo(creditorName, SWIFTParticipantOrganisationIdentification(creditorLei)),
                SWIFTParticipantAgent(creditorBicfi),
                SWIFTParticipantAccount(creditorIban),
                remittanceInformation)

        val mapper = jacksonObjectMapper()
        val paymentUrl = "$apiUrl/payment_initiation"
        val paymentInstructionId = swiftPaymentInstruction.paymentIdentification.e2eIdentification

        logger.info(messageWithParams("Submitting payment instruction $swiftPaymentInstruction to $paymentUrl", "PAYMENT_INSTRUCTION_ID" to paymentInstructionId))

        // making HTTP request
        val (req, res, result) = paymentUrl
                .httpPost()
                .header("accept" to "application/json")
                .header("content-type" to "application/json")
                .header("x-api-key" to apiKey)
                .body(mapper.writeValueAsString(swiftPaymentInstruction))
                .response()

        val responseData = String(res.data)

        // if the payment attempt resulted to error - logging and throwing FlowException
        if (res.httpStatusCode >= 400) {
            val message = httpResultMessage("Error while submitting payment instruction", res.httpStatusCode, responseData, "PAYMENT_INSTRUCTION_ID" to paymentInstructionId)
            logger.warn(message)
            throw SWIFTPaymentException(message)
        } else {
            logger.info(httpResultMessage("Successfully submitted payment instruction", res.httpStatusCode, responseData, "PAYMENT_INSTRUCTION_ID" to paymentInstructionId))
            return mapper.readValue(responseData)
        }
    }


    private fun getUnsignedPayload(uetr : String) : ByteArray {
        val url = "$apiUrl/payment_initiation/$uetr/payload_unsigned"

        SWIFTClient.logger.info(messageWithParams("Getting unsigned payload", "UETR" to uetr))
        val (req, res, result) = url
                .httpGet()
                .header("x-api-key" to apiKey)
                .header("accept" to "application/json")
                .header("content-type" to "application/json")
                .response()

        val responseData = String(res.data)
        if (res.httpStatusCode >= 400) {
            val message = httpResultMessage("Error while retrieving unsigned payment payload", res.httpStatusCode, responseData, "UETR" to uetr)
            SWIFTClient.logger.warn(message)
            throw SWIFTPaymentException(message)
        } else {
            SWIFTClient.logger.info(httpResultMessage("Successfully retrieved unsigned payment payload", res.httpStatusCode, responseData, "UETR" to uetr))
            return res.data
        }
    }

    private fun submitSignedPayload(uetr : String, signedPayload : String) {
        val paymentUrl = "$apiUrl/payment_initiation/$uetr/payload_signed"

        logger.info(messageWithParams("Submitting signed payload", "UETR" to uetr))

        // making HTTP request
        val (req, res, result) = paymentUrl
                .httpPost()
                .header("accept" to "application/json")
                .header("content-type" to "application/json")
                .header("x-api-key" to apiKey)
                .body(signedPayload)
                .response()

        val responseData = String(res.data)

        // if the payment attempt resulted to error - logging and throwing FlowException
        if (res.httpStatusCode >= 400) {
            val message = httpResultMessage("Error while submitting signed payload", res.httpStatusCode, responseData, "UETR" to uetr)
            logger.warn(message)
            throw SWIFTPaymentException(message)
        } else {
            logger.info(httpResultMessage("Successfully submitted signed payload", res.httpStatusCode, responseData, "UETR" to uetr))
        }
    }

    /**
     * Fetches SWIFT payment status
     */
    fun getPaymentStatus(uetr : String) : SWIFTPaymentStatus {
        val checkStatusUrl = "$apiUrl/payment_initiation/$uetr/tracker_status"

        SWIFTClient.logger.info(messageWithParams("Getting payment status", "UETR" to uetr))
        val (req, res, result) = checkStatusUrl
                .httpGet()
                .header("x-api-key" to apiKey)
                .header("accept" to "application/json")
                .header("content-type" to "application/json")
                .response()

        val responseData = String(res.data)
        val mapper = jacksonObjectMapper()
        if (res.httpStatusCode >= 400) {
            val message = httpResultMessage("Error while retrieving payment status.", res.httpStatusCode, responseData, "UETR" to uetr)
            SWIFTClient.logger.warn(message)
            throw SWIFTPaymentException(message)
        } else {
            SWIFTClient.logger.info(httpResultMessage("Successfully retrieved payment status", res.httpStatusCode, responseData, "UETR" to uetr))
            return mapper.readValue(responseData)
        }
    }

    /**
     * TODO: This method should be eventually removed. This API is open for testing only.
     */
    fun updatePaymentStatus(uetr : String, status : SWIFTPaymentStatusType) {
        val checkStatusUrl = "$apiUrl/payment_initiation/$uetr/tracker_status?newstatus=$status"

        SWIFTClient.logger.info(messageWithParams("Updating payment status.", "UETR" to uetr))
        val (req, res, result) = checkStatusUrl
                .httpPost()
                .header("x-api-key" to apiKey)
                .header("accept" to "application/json")
                .header("content-type" to "application/json")
                .response()

        val responseData = String(res.data)
        if (res.httpStatusCode >= 400) {
            val message = httpResultMessage("Error during updating payment status", res.httpStatusCode, responseData, "UETR" to uetr)
            SWIFTClient.logger.warn(message)
            throw SWIFTPaymentException(message)
        } else {
            SWIFTClient.logger.info(httpResultMessage("Successfully updated payment status", res.httpStatusCode, responseData, "UETR" to uetr))
        }
    }

    private fun httpResultMessage(message : String, httpResultCode : Int, responseBody : String, vararg otherParams : Pair<String, Any>) : String {
        val newParams = listOf(Pair<String, Any>("SWIFT_HTTP_STATUS", httpResultCode), Pair<String, Any>("SWIFT_HTTP_RESPONSE", responseBody)) + otherParams.asList()
        return messageWithParams(message, *newParams.toTypedArray())
    }

    private fun messageWithParams(message : String, vararg otherParams : Pair<String, Any>) : String {
        return if (otherParams.isNotEmpty()) {
            val initial = "."
            message + otherParams.fold(initial) { acc, s ->
                val pair = "${s.first.toUpperCase()}=${s.second}"
                if (acc == initial) "$acc $pair" else "$acc, $pair"}
        } else message
    }
 }

class SWIFTPaymentException(message : String) : FlowException(message)