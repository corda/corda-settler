package com.r3.corda.finance.ripple.types

import com.r3.corda.finance.obligation.OffLedgerSettlementInstructions
import com.r3.corda.finance.obligation.PaymentReference
import com.r3.corda.finance.obligation.PaymentStatus
import com.r3.corda.finance.ripple.flows.MakeXRPPayment
import com.ripple.core.coretypes.AccountID
import net.corda.core.identity.Party

/**
 * Terms specific to settling with XRP. In this case, parties must agree on:
 * - which ripple address the payment must be made to
 * - which servers should be used to check the payment was successful
 *
 * The terms can be updated with:
 * - the hash of the ripple transaction when the ripple payment is submitted
 * - a payment status
 */
data class XRPSettlementInstructions(
        override val accountToPay: AccountID,
        override val settlementOracle: Party,
        val lastLedgerSequence: Long,
        override val paymentFlow: Class<MakeXRPPayment> = MakeXRPPayment::class.java,
        override val paymentStatus: PaymentStatus = PaymentStatus.NOT_SENT,
        override val paymentReference: PaymentReference? = null
) : OffLedgerSettlementInstructions<MakeXRPPayment> {
    override fun addPaymentReference(ref: PaymentReference): OffLedgerSettlementInstructions<MakeXRPPayment> {
        return copy(paymentReference = ref, paymentStatus = PaymentStatus.SENT)
    }

    fun updateStatus(newStatus: PaymentStatus): OffLedgerSettlementInstructions<MakeXRPPayment> {
        return copy(paymentStatus = newStatus)
    }
}