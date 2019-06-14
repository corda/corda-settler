package com.r3.corda.finance.ripple.types

import com.r3.corda.finance.obligation.contracts.types.Payment
import com.r3.corda.finance.obligation.contracts.types.PaymentReference
import com.r3.corda.finance.obligation.contracts.types.PaymentStatus
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.Amount

/** Represents a payment of XRP. */
data class XrpPayment<T : TokenType>(
        override val paymentReference: PaymentReference,
        /** It is expected that the payment reaches the beneficiary by this ledger number. */
        val lastLedgerSequence: Long,
        override val amount: Amount<T>,
        override var status: PaymentStatus = PaymentStatus.SENT
) : Payment<T> {
    override fun toString(): String {
        return "Amount: $amount, Ripple tx hash: $paymentReference, Status: $status"
    }
}