package com.r3.corda.finance.swift.types

import com.r3.corda.finance.obligation.contracts.types.Payment
import com.r3.corda.finance.obligation.contracts.types.PaymentReference
import com.r3.corda.finance.obligation.contracts.types.PaymentStatus
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.Amount

/** Represents a payment of SWIFT. */
data class SwiftPayment<T : TokenType>(
        // This is UETR
        override val paymentReference: PaymentReference,
        override val amount: Amount<T>,
        override var status: PaymentStatus = PaymentStatus.SENT
) : Payment<T> {
    override fun toString(): String {
        return "Amount: $amount, Swift tx hash: $paymentReference, Status: $status"
    }
}