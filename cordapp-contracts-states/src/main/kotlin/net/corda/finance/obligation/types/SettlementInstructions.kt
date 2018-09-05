package net.corda.finance.obligation.types

import com.ripple.core.coretypes.AccountID
import net.corda.core.crypto.SecureHash
import net.corda.core.serialization.CordaSerializable

/** Generic terms which provide a day by when the obligation must be settled. */
@CordaSerializable
interface SettlementInstructions

/** Terms specific to on-ledger settlement. */
interface OnLedgerSettlementTerms : SettlementInstructions

/**
 * Terms specific to off-ledger settlement. Here some kind of account must be specified. The account might be in the
 * form of a bank account number or a crypto currency address.
 */
interface OffLedgerSettlementTerms : SettlementInstructions {
    val accountToPay: Any
}

/**
 * Terms specific to settling with XRP. In this case, parties must agree on:
 * - which ripple address the payment must be made to
 *
 * The terms can be updated with:
 * - the hash of the ripple transaction when the ripple payment is submitted
 * - a payment status
 */
data class RippleSettlementInstructions(
        override val accountToPay: AccountID,
        val paymentStatus: PaymentStatus = PaymentStatus.NOT_SENT,
        val rippleTransactionHash: SecureHash? = null
) : OffLedgerSettlementTerms {
    @CordaSerializable
    enum class PaymentStatus { NOT_SENT, SENT, ACCEPTED, REJECTED }
}