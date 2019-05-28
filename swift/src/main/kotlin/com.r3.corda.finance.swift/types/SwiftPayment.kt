package com.r3.corda.finance.swift.types

import com.r3.corda.finance.obligation.contracts.types.Payment
import com.r3.corda.finance.obligation.contracts.types.PaymentReference
import com.r3.corda.finance.obligation.contracts.types.PaymentStatus
import com.r3.corda.sdk.token.money.FiatCurrency
import net.corda.core.contracts.Amount

/** Represents a payment of SWIFT. */
data class SwiftPayment(
        // This is UETR
        override val paymentReference: PaymentReference,
        override val amount: Amount<FiatCurrency>,
        override var status: PaymentStatus = PaymentStatus.SENT
) : Payment<FiatCurrency> {
    override fun toString(): String {
        return "Amount: $amount, Swift tx hash: $paymentReference, Status: $status"
    }
}