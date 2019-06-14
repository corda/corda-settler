package com.r3.corda.finance.swift.services

import com.r3.corda.finance.swift.services.SWIFTService.Companion.certificate
import com.r3.corda.finance.swift.services.SWIFTService.Companion.privateKey
import com.r3.corda.finance.swift.types.SWIFTPaymentStatusType
import com.r3.corda.lib.tokens.money.GBP
import org.junit.Ignore
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals

@Ignore
class SWIFTClientTest {
    private val swiftService = SWIFTClient(
            "https://cos.swiftlabapis.com/beta2",
            "EMAIL IVAN/ROGER FOR API KEY",
            privateKey(),
            certificate())

    @Test
    fun `test submit payment and get status`() {
        val submissionResult = swiftService.makePayment("MyInVoice2You",
                Date(),
                1000.GBP,
                "PayingCorporate",
                "5299000J2N45DDNE4Y28",
                "BE0473244135",
                "CITIGB2L",
                "Receiving corp",
                "6299300D2N76ADNE4Y55",
                "BE0473244135",
                "CITIGB2L",
                "arn:aws:acm-pca:eu-west-1:522843637103:certificate-authority/e2a9c0fd-b62e-44a9-bcc2-02e46a1f61c2")
        println("Payment result $submissionResult")

        val paymentStatus =  swiftService.getPaymentStatus(submissionResult.uetr)
        println("Payment status: $paymentStatus")

        assertEquals(SWIFTPaymentStatusType.ACSP, paymentStatus.transactionStatus.status)
    }
}