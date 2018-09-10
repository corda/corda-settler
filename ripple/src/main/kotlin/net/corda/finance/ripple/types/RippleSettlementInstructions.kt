package net.corda.finance.ripple.types

import com.ripple.core.coretypes.AccountID
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.finance.obligation.flows.MakeOffLedgerPayment
import net.corda.finance.obligation.types.OffLedgerSettlementTerms
import net.corda.finance.obligation.types.PaymentStatus

/**
 * Terms specific to settling with XRP. In this case, parties must agree on:
 * - which ripple address the payment must be made to
 * - which servers should be used to check the payment was successful
 *
 * The terms can be updated with:
 * - the hash of the ripple transaction when the ripple payment is submitted
 * - a payment status
 */
data class RippleSettlementInstructions<T : MakeOffLedgerPayment>(
        override val accountToPay: AccountID,
        override val settlementOracle: Party,
        override val paymentFlow: T,
        val paymentStatus: PaymentStatus = PaymentStatus.NOT_SENT,
        val rippleTransactionHash: SecureHash? = null
) : OffLedgerSettlementTerms<T> {
    fun addRippleTransactionHash(hash: SecureHash): RippleSettlementInstructions<T> {
        return copy(rippleTransactionHash = hash, paymentStatus = PaymentStatus.SENT)
    }
}