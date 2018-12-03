package com.r3.corda.finance.ripple.types

import com.r3.corda.finance.obligation.types.Money
import com.r3.corda.finance.obligation.types.Payment
import com.r3.corda.finance.obligation.types.PaymentReference
import com.r3.corda.finance.obligation.types.PaymentStatus
import net.corda.core.contracts.Amount

/** Represents a payment of XRP. */
data class XrpPayment<T : Money>(
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