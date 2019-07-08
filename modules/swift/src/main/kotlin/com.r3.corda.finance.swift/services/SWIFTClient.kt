package com.r3.corda.finance.swift.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import com.r3.corda.finance.swift.types.*
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.flows.FlowException
import org.bouncycastle.cert.jcajce.JcaCertStore
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

class SWIFTClient(
        private val apiUrl : String,
        private val apiKey : String,
        private val privateKey : PrivateKey,
        private val certificate : X509Certificate) {
    companion object {
        private val logger = LoggerFactory.getLogger(SWIFTClient::class.java)!!
    }

    /**
     * Submits a payment to the SWIFT gateway
     */
    fun makePayment(e2eId : String,
                    executionDate : Date,
                    amount: Amount<TokenType>,
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
        val signedPayload = signBytes(unsignedPayload.payload.toByteArray())
        val base64Payload = Base64.getEncoder().encodeToString(signedPayload)
        submitSignedBase64Payload(paymentResponse.uetr, base64Payload)
        return paymentResponse
    }

    private fun signBytes(data : ByteArray) : ByteArray {
        val certList = listOf(certificate)
        val certs = JcaCertStore(certList)
        val signGen = CMSSignedDataGenerator()

        val sha1Signer = JcaContentSignerBuilder("SHA1withRSA").setProvider("BC").build(privateKey)
        signGen.addSignerInfoGenerator(JcaSignerInfoGeneratorBuilder(
                JcaDigestCalculatorProviderBuilder().build()).build(sha1Signer, certificate))
        signGen.addCertificates(certs)

        val content = CMSProcessableByteArray(data)
        val signedData = signGen.generate(content, true)
        return signedData.encoded
    }


    private fun submitPaymentInstruction(e2eId : String,
                                         executionDate : Date,
                                         amount: Amount<TokenType>,
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
                SWIFTPaymentAmount(SWIFTInstructedPaymentAmount(amount.token.tokenIdentifier, amount = amountFormatter.format(doubleAmount))),
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
        val (_, res, _) = paymentUrl
                .httpPost()
                .header("accept" to "application/json")
                .header("content-type" to "application/json")
                .header("x-api-key" to apiKey)
                .body(mapper.writeValueAsString(swiftPaymentInstruction))
                .response()

        val responseData = String(res.data)

        // if the payment attempt resulted to error - logging and throwing FlowException
        if (res.statusCode >= 400) {
            val message = httpResultMessage("Error while submitting payment instruction", res.statusCode, responseData, "PAYMENT_INSTRUCTION_ID" to paymentInstructionId)
            logger.warn(message)
            throw SWIFTPaymentException(message)
        } else {
            logger.info(httpResultMessage("Successfully submitted payment instruction", res.statusCode, responseData, "PAYMENT_INSTRUCTION_ID" to paymentInstructionId))
            return mapper.readValue(responseData)
        }
    }


    private fun getUnsignedPayload(uetr : String) : SWIFTUnsignedPayload {
        val url = "$apiUrl/payment_initiation/$uetr/payload_unsigned"

        SWIFTClient.logger.info(messageWithParams("Getting unsigned payload", "UETR" to uetr))
        val (_, res, _) = url
                .httpGet()
                .header("x-api-key" to apiKey)
                .header("accept" to "application/json")
                .header("content-type" to "application/json")
                .response()

        val responseData = String(res.data)
        if (res.statusCode >= 400) {
            val message = httpResultMessage("Error while retrieving unsigned payment payload", res.statusCode, responseData, "UETR" to uetr)
            SWIFTClient.logger.warn(message)
            throw SWIFTPaymentException(message)
        } else {
            val mapper = jacksonObjectMapper()
            SWIFTClient.logger.info(httpResultMessage("Successfully retrieved unsigned payment payload", res.statusCode, responseData, "UETR" to uetr))
            return mapper.readValue(res.data)
        }
    }

    private fun submitSignedBase64Payload(uetr : String, signedBase64Payload : String) {
        val paymentUrl = "$apiUrl/payment_initiation/$uetr/payload_signed"

        logger.info(messageWithParams("Submitting signed payload", "UETR" to uetr))

        // making HTTP request
        val (_, res, _) = paymentUrl
                .httpPost()
                .header("accept" to "application/json")
                .header("content-type" to "application/json")
                .header("x-api-key" to apiKey)
                .body(signedBase64Payload)
                .response()

        val responseData = String(res.data)

        // if the payment attempt resulted to error - logging and throwing FlowException
        if (res.statusCode >= 400) {
            val message = httpResultMessage("Error while submitting signed payload", res.statusCode, responseData, "UETR" to uetr)
            logger.warn(message)
            throw SWIFTPaymentException(message)
        } else {
            logger.info(httpResultMessage("Successfully submitted signed payload", res.statusCode, responseData, "UETR" to uetr))
        }
    }

    /**
     * Fetches SWIFT payment status
     */
    fun getPaymentStatus(uetr : String) : SWIFTPaymentStatus {
        val checkStatusUrl = "$apiUrl/payment_initiation/$uetr/tracker_status"

        SWIFTClient.logger.info(messageWithParams("Getting payment status", "UETR" to uetr))
        val (_, res, _) = checkStatusUrl
                .httpGet()
                .header("x-api-key" to apiKey)
                .header("accept" to "application/json")
                .header("content-type" to "application/json")
                .response()

        val responseData = String(res.data)
        val mapper = jacksonObjectMapper()
        if (res.statusCode >= 400) {
            val message = httpResultMessage("Error while retrieving payment status.", res.statusCode, responseData, "UETR" to uetr)
            SWIFTClient.logger.warn(message)
            throw SWIFTPaymentException(message)
        } else {
            SWIFTClient.logger.info(httpResultMessage("Successfully retrieved payment status", res.statusCode, responseData, "UETR" to uetr))
            return mapper.readValue(responseData)
        }
    }

    /**
     * TODO: This method should be eventually removed. This API is open for testing only.
     */
    fun updatePaymentStatus(uetr : String, status : SWIFTPaymentStatusType) {
        val checkStatusUrl = "$apiUrl/payment_initiation/$uetr/tracker_status?newstatus=$status"

        SWIFTClient.logger.info(messageWithParams("Updating payment status.", "UETR" to uetr))
        val (_, res, _) = checkStatusUrl
                .httpPost()
                .header("x-api-key" to apiKey)
                .header("accept" to "application/json")
                .header("content-type" to "application/json")
                .response()

        val responseData = String(res.data)
        if (res.statusCode >= 400) {
            val message = httpResultMessage("Error during updating payment status", res.statusCode, responseData, "UETR" to uetr)
            SWIFTClient.logger.warn(message)
            throw SWIFTPaymentException(message)
        } else {
            SWIFTClient.logger.info(httpResultMessage("Successfully updated payment status", res.statusCode, responseData, "UETR" to uetr))
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