package com.r3.corda.lib.settler.manual.types

import com.r3.corda.lib.settler.manual.flows.MakeManualPayment
import com.r3.corda.lib.settler.contracts.types.OffLedgerPayment
import net.corda.core.identity.Party

/**
 * Terms specific to settling manually.
 */
data class ManualSettlement(
        override val accountToPay: String,
        val remittanceInformation : String
) : OffLedgerPayment<MakeManualPayment<*>> {
    override val settlementOracle: Party? = null
    override val paymentFlow: Class<MakeManualPayment<*>> = MakeManualPayment::class.java

    override fun toString(): String {
        return "Pay manually $accountToPay."
    }
}