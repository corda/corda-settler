package com.r3.corda.finance.ripple.types

import com.r3.corda.finance.obligation.DigitalCurrency
import com.r3.corda.finance.obligation.Payment
import com.r3.corda.finance.obligation.PaymentReference
import com.r3.corda.finance.obligation.PaymentStatus
import net.corda.core.contracts.Amount

/** Represents a payment of XRP. */
data class XrpPayment(
        override val paymentReference: PaymentReference,
        override val amount: Amount<DigitalCurrency>,
        /** It is expected that the payment reaches the beneficiary by this ledger number. */
        val lastLedgerSequence: Long,
        override val status: PaymentStatus = PaymentStatus.SENT
) : Payment<DigitalCurrency> {
    override fun updateStatus(newStatus: PaymentStatus) = copy(status = newStatus)
}