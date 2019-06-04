package com.r3.corda.finance.manual.types

import com.r3.corda.finance.obligation.types.Payment
import com.r3.corda.finance.obligation.types.PaymentReference
import com.r3.corda.finance.obligation.types.PaymentStatus
import com.r3.corda.sdk.token.money.FiatCurrency
import net.corda.core.contracts.Amount

/** Represents a manual payment. */
data class ManualPayment(
        override val paymentReference: PaymentReference,
        override val amount: Amount<FiatCurrency>,
        override var status: PaymentStatus = PaymentStatus.SENT
) : Payment<FiatCurrency> {
    override fun toString(): String {
        return "Amount: $amount, manual reference: $paymentReference, Status: $status"
    }
}