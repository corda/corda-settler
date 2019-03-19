package com.r3.corda.finance.obligation

import com.r3.corda.finance.obligation.states.Obligation
import com.r3.corda.finance.obligation.types.Money
import com.r3.corda.finance.swift.services.SWIFTClient
import com.r3.corda.finance.swift.services.SWIFTService.Companion.certificate
import com.r3.corda.finance.swift.services.SWIFTService.Companion.privateKey
import com.r3.corda.finance.swift.types.SWIFTPaymentStatusType
import com.r3.corda.finance.swift.types.SwiftPayment
import com.r3.corda.finance.swift.types.SwiftSettlement
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SWIFTObligationTestsWithOracle : AbstractObligationTestsWithOracle<SwiftSettlement>(GBP) {
    private val creditorName = "Receiving corp"
    private val creditorLei = "6299300D2N76ADNE4Y55"
    private val creditorIban = "BE0473244135"
    private val creditorBicfi = "CITIGB2L"
    private val remittanceInformation = "arn:aws:acm-pca:eu-west-1:522843637103:certificate-authority/e2a9c0fd-b62e-44a9-bcc2-02e46a1f61c2"
    private val delayBeforeApprovingPayment = 10L


    override fun castToSettlementType(obj : Any?) = obj as SwiftSettlement?

    override fun createSettlement(party : Party) =
        SwiftSettlement(creditorIban, O.legalIdentity(), creditorName, creditorLei, creditorBicfi, remittanceInformation)

    override fun manuallyApprovePayments(numberOfPayments : Int) {
        (0..numberOfPayments).forEach {
            updateLastPaymentStatus(delayBeforeApprovingPayment + it * delayBeforeApprovingPayment)
        }
    }

    private fun updateLastPaymentStatus(delay : Long = delayBeforeApprovingPayment) {
        val executor = Executors.newSingleThreadScheduledExecutor()
        // manually updating the settlement status in 5 sec
        executor.schedule({
            // we need to craft a swift client manually here because status update endpoint requires a different apiUrl
            val swiftClient = SWIFTClient(
                    "https://gpi.swiftlabapis.com/beta2",
                    "EMAIL IVAN/ROGER FOR API KEY",
                    privateKey(),
                    certificate())

            // we know that there is only one obligation there
            val swiftObligation = A.services.vaultService.queryBy<Obligation<Money>>().states.single()
            val lastPayment = swiftObligation.state.data.payments.last() as  SwiftPayment
            swiftClient.updatePaymentStatus(lastPayment.paymentReference, SWIFTPaymentStatusType.ACCC)
        }, delay, TimeUnit.SECONDS)
    }
}