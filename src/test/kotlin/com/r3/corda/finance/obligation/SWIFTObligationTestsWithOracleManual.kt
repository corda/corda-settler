package com.r3.corda.finance.obligation

import com.r3.corda.finance.obligation.contracts.states.Obligation
import com.r3.corda.finance.swift.services.SWIFTService
import com.r3.corda.finance.swift.types.SWIFTPaymentStatusType
import com.r3.corda.finance.swift.types.SwiftPayment
import com.r3.corda.finance.swift.types.SwiftSettlement
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.r3.corda.lib.tokens.money.GBP
import net.corda.core.identity.Party
import net.corda.core.node.services.queryBy
import org.junit.Ignore
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/*
This test uses the SWIFT gLink Manual API instance.
The gLink Manual API url must be set in the swift.conf file in the resources directory for this test to pass.
Additionally, a SWIFT certificate (swiftCert.pem) & SWIFT private key (swiftKey.pem) must also be present in the resources directory.
Manual payment confirmation should only be used for testing purposes.
Test will not run unless @Ignore decorator is commented out.
*/

//@Ignore("The private key is not available.")
class SWIFTObligationTestsWithOracleManual : AbstractObligationTestsWithOracle<SwiftSettlement>(GBP) {
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
            val swiftClient = A.services.cordaService(SWIFTService::class.java).swiftClient()
            // we know that there is only one obligation there
            val swiftObligation = A.services.vaultService.queryBy<Obligation<TokenType>>().states.single()
            val lastPayment = swiftObligation.state.data.payments.last() as SwiftPayment
            swiftClient.updatePaymentStatus(lastPayment.paymentReference, SWIFTPaymentStatusType.ACCC, lastPayment.amount.toDecimal().toPlainString())
        }, delay, TimeUnit.SECONDS)
    }
}