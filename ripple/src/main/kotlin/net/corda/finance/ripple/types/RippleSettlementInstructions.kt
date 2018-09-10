package net.corda.finance.ripple.types

import com.ripple.core.coretypes.AccountID
import net.corda.core.identity.Party
import net.corda.finance.obligation.types.OffLedgerSettlementInstructions
import net.corda.finance.obligation.types.PaymentReference
import net.corda.finance.obligation.types.PaymentStatus
import net.corda.finance.ripple.flows.MakeRipplePayment

/**
 * Terms specific to settling with XRP. In this case, parties must agree on:
 * - which ripple address the payment must be made to
 * - which servers should be used to check the payment was successful
 *
 * The terms can be updated with:
 * - the hash of the ripple transaction when the ripple payment is submitted
 * - a payment status
 */
data class RippleSettlementInstructions(
        override val accountToPay: AccountID,
        override val settlementOracle: Party,
        override val paymentFlow: Class<MakeRipplePayment> = MakeRipplePayment::class.java,
        override val paymentStatus: PaymentStatus = PaymentStatus.NOT_SENT,
        override val paymentReference: PaymentReference? = null
) : OffLedgerSettlementInstructions<MakeRipplePayment> {
    override fun addPaymentReference(ref: PaymentReference): OffLedgerSettlementInstructions<MakeRipplePayment> {
        return copy(paymentReference = ref, paymentStatus = PaymentStatus.SENT)
    }
}