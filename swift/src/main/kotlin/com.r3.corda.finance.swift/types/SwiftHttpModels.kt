package com.r3.corda.finance.swift.types

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Data class that represents a POST payload to submit SWIFT payment
 */
data class SwiftPaymentInstruction(
        @JsonProperty("payment_identification")
        val paymentIdentification : SWIFTPaymentIdentification,
        @JsonProperty("requested_execution_date")
        val requestedExecutionDate : SWIFTRequestedExecutionDate,
        @JsonProperty("amount")
        val amount : SWIFTPaymentAmount,
        @JsonProperty("debtor")
        val debtor : SWIFTParticipantInfo,
        @JsonProperty("debtor_agent")
        val debtorAgent : SWIFTParticipantAgent,
        @JsonProperty("debtor_account")
        val debtorAccount : SWIFTParticipantAccount,
        @JsonProperty("creditor")
        val creditor : SWIFTParticipantInfo,
        @JsonProperty("creditor_agent")
        val creditorAgent : SWIFTParticipantAgent,
        @JsonProperty("creditor_account")
        val creditorAccount : SWIFTParticipantAccount,
        @JsonProperty("remittance_information")
        val remittanceInformation : String
)

data class SWIFTPaymentIdentification(
        @JsonProperty("end_to_end_identification")
        val e2eIdentification : String
)

data class SWIFTRequestedExecutionDate(
        @JsonProperty("date")
        val date: String
)

data class SWIFTPaymentAmount(
        @JsonProperty("instructed_amount")
        val instructedAmount : SWIFTInstructedPaymentAmount
)

data class SWIFTInstructedPaymentAmount(
        @JsonProperty("currency")
        val currency : String,
        @JsonProperty("amount")
        val amount : String
)

data class SWIFTParticipantInfo(
        @JsonProperty("name")
        val name : String,
        @JsonProperty("organisation_identification")
        val organisationIdentification : SWIFTParticipantOrganisationIdentification
)

data class SWIFTParticipantOrganisationIdentification(
        @JsonProperty("lei")
        val lei : String
)

data class SWIFTParticipantAccount(
        @JsonProperty("iban")
        val iban : String
)

data class SWIFTParticipantAgent(
        @JsonProperty("bicfi")
        val bicfi : String
)

/**
 * 2xx response body
 */
data class SWIFTPaymentResponse(
        @JsonProperty("credit_transfer_transaction_resource_identification")
        val creditTransferTransactionResourceIdentification : String?,
        @JsonProperty("uetr")
        val uetr : String
)

/**
 * 4xx, 5xx response body
 */
data class SWIFTErrorResponse(
        @JsonProperty("status")
        val status : SWIFTErrorResponseStatus
)

data class SWIFTErrorResponseStatus(
        @JsonProperty("severity")
        val severity : String,
        @JsonProperty("code")
        val code: String,
        @JsonProperty("text")
        val text : String
)

/**
 * Data model for payment status check.
 * TODO: add all fields from the SWIFT payload if needed
 */
data class SWIFTPaymentStatus(
        @JsonProperty("uetr")
        val uetr : String,
        @JsonProperty("transaction_status")
        val transactionStatus : SWIFTTransactionStatus
)


data class SWIFTUnsignedPayload(
        @JsonProperty("payload")
        val payload : String
)



data class SWIFTTransactionStatus(
        @JsonProperty("status")
        val status : SWIFTPaymentStatusType)

enum class SWIFTPaymentStatusType {
        RJCT, ACSP, ACCC
}