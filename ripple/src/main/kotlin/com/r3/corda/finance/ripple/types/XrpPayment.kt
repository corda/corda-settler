package com.r3.corda.finance.ripple.types

import com.r3.corda.finance.obligation.*
import net.corda.core.contracts.Amount

/** Represents a payment of XRP. */
data class XrpPayment<T : Money>(
        override val paymentReference: PaymentReference,
        override val amount: Amount<T>,
        /** It is expected that the payment reaches the beneficiary by this ledger number. */
        val lastLedgerSequence: Long,
        override val status: PaymentStatus = PaymentStatus.SENT
) : Payment<T> {
    override fun updateStatus(newStatus: PaymentStatus) = copy(status = newStatus)
}