package com.r3.corda.finance.swift.types

import com.r3.corda.finance.obligation.contracts.types.OffLedgerPayment
import com.r3.corda.finance.swift.flows.MakeSWIFTPayment
import net.corda.core.identity.Party

/**
 * Terms specific to settling with SWIFT.
 */
data class SwiftSettlement(
        // creditor IBAN
        override val accountToPay: String,
        override val settlementOracle: Party,
        val creditorName : String,
        val creditorLei : String,
        val creditorBicfi : String,
        val remittanceInformation : String,
        override val paymentFlow: Class<MakeSWIFTPayment<*>> = MakeSWIFTPayment::class.java
) : OffLedgerPayment<MakeSWIFTPayment<*>> {
    override fun toString(): String {
        return "Pay SWIFT IBAN $accountToPay and use $settlementOracle as settlement Oracle."
    }
}