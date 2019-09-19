package com.r3.corda.lib.settler.manual.types

import com.r3.corda.lib.obligation.types.Payment
import com.r3.corda.lib.obligation.types.PaymentReference
import com.r3.corda.lib.obligation.types.PaymentStatus
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.Amount

/** Represents a manual payment. */
data class ManualPayment<T : TokenType>(
        override val paymentReference: PaymentReference,
        override val amount: Amount<T>,
        override var status: PaymentStatus = PaymentStatus.SENT
) : Payment<T> {
    override fun toString(): String {
        return "Amount: $amount, manual reference: $paymentReference, Status: $status"
    }
}