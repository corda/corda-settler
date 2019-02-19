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
import net.corda.core.flows.FlowException
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

class SWIFTClient(
        private val apiUrl : String,
        private val apiKey : String) {
    companion object {
        val logger = LoggerFactory.getLogger(SWIFTClient::class.java)
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

        logger.info("Submitting payment instruction $swiftPaymentInstruction to $paymentUrl. PAYMENT_INSTRUCTION_ID=$paymentInstructionId")

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
            logger.warn("Error while submitting payment. PAYMENT_INSTRUCTION_ID=$paymentInstructionId, SWIFT_HTTP_STATUS=${res.httpStatusCode}, SWIFT_HTTP_ERROR_RESPONSE=$responseData")
            throw SWIFTPaymentException("Error while submitting payment instruction. Http status=${res.httpStatusCode}, response data=$responseData.")
        } else {
            logger.info("Successfully submitted payment instruction. PAYMENT_INSTRUCTION_ID=$paymentInstructionId, SWIFT_HTTP_STATUS=${res.httpStatusCode}, SWIFT_HTTP_ERROR_RESPONSE=$responseData")
            return mapper.readValue(responseData)
        }
    }

    /**
     * Fetches SWIFT payment status
     */
    fun getPaymentStatus(uetr: String) : SWIFTPaymentStatus {
        val checkStatusUrl = "$apiUrl/payment_initiation/$uetr/tracker_status"

        SWIFTClient.logger.info("Getting payment status. UETR=$uetr")
        val (req, res, result) = checkStatusUrl
                .httpGet()
                .header("x-api-key" to apiKey)
                .header("accept" to "application/json")
                .header("content-type" to "application/json")
                .response()

        val responseData = String(res.data)
        val mapper = jacksonObjectMapper()
        if (res.httpStatusCode >= 400) {
            SWIFTClient.logger.warn("Error while retrieving payment status. UETR=$uetr, SWIFT_HTTP_STATUS=${res.httpStatusCode}, SWIFT_HTTP_ERROR_RESPONSE=$responseData")
            throw SWIFTPaymentException("Error while retrieving payment status request. Http status=${res.httpStatusCode}, response data=$responseData.")
        } else {
            SWIFTClient.logger.info("Successfully retrieved payment status. UETR=$uetr, SWIFT_HTTP_STATUS=${res.httpStatusCode}, SWIFT_HTTP_ERROR_RESPONSE=$responseData")
            return mapper.readValue(responseData)
        }
    }

    /**
     * TODO: This method should be eventually removed. This API is open for testing only.
     */
    fun updatePaymentStatus(uetr: String, status : SWIFTPaymentStatusType) {
        val checkStatusUrl = "$apiUrl/payment_initiation/$uetr/tracker_status?newstatus=$status"

        SWIFTClient.logger.info("Updating payment status. UETR=$uetr")
        val (req, res, result) = checkStatusUrl
                .httpPost()
                .header("x-api-key" to apiKey)
                .header("accept" to "application/json")
                .header("content-type" to "application/json")
                .response()

        val responseData = String(res.data)
        if (res.httpStatusCode >= 400) {
            SWIFTClient.logger.warn("Error during updating payment status. UETR=$uetr, SWIFT_HTTP_STATUS=${res.httpStatusCode}, SWIFT_HTTP_ERROR_RESPONSE=$responseData")
            throw SWIFTPaymentException("Error during updating payment status request. Http status=${res.httpStatusCode}, response data=$responseData.")
        } else {
            SWIFTClient.logger.info("Successfully updated payment status. UETR=$uetr, SWIFT_HTTP_STATUS=${res.httpStatusCode}, SWIFT_HTTP_ERROR_RESPONSE=$responseData")
        }
    }
}

class SWIFTPaymentException(message : String) : FlowException(message)