package com.r3.corda.finance.obligation

import com.r3.corda.finance.swift.types.SwiftSettlement
import com.r3.corda.lib.tokens.money.GBP
import net.corda.core.identity.Party
import org.junit.Ignore

/*
This test uses the SWIFT gLink Automatic API instance.
The gLink Automatic API url must be set in the swift.conf file in the resources directory for this test to pass.
Additionally, a SWIFT certificate (swiftCert.pem) & SWIFT private key (swiftKey.pem) must also be present in the resources directory.
Test will not run unless @Ignore decorator is commented out.
*/

//@Ignore("The private key is not available.")
class SWIFTObligationTestsWithOracleAutomatic : AbstractObligationTestsWithOracle<SwiftSettlement>(GBP) {
    private val creditorName = "Receiving corp"
    private val creditorLei = "6299300D2N76ADNE4Y55"
    private val creditorIban = "BE0473244135"
    private val creditorBicfi = "CITIGB2L"
    private val remittanceInformation = "arn:aws:acm-pca:eu-west-1:522843637103:certificate-authority/e2a9c0fd-b62e-44a9-bcc2-02e46a1f61c2"

    override fun castToSettlementType(obj : Any?) = obj as SwiftSettlement?

    override fun createSettlement(party : Party) =
            SwiftSettlement(creditorIban, O.legalIdentity(), creditorName, creditorLei, creditorBicfi, remittanceInformation)

}