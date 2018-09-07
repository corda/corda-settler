package net.corda.finance.obligation.client.types

import com.ripple.core.coretypes.AccountID
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.net.URI

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
 * - which servers should be used to check the payment was successful
 *
 * The terms can be updated with:
 * - the hash of the ripple transaction when the ripple payment is submitted
 * - a payment status
 */
data class RippleSettlementInstructions(
        override val accountToPay: AccountID,
        val acceptableServers: List<URI>,
        val settlementOracle: Party,
        val paymentStatus: PaymentStatus = PaymentStatus.NOT_SENT,
        val rippleTransactionHash: SecureHash? = null
) : OffLedgerSettlementTerms {
    @CordaSerializable
    enum class PaymentStatus { NOT_SENT, SENT, ACCEPTED, REJECTED }

    fun addRippleTransactionHash(hash: SecureHash): RippleSettlementInstructions {
        return copy(
                rippleTransactionHash = hash,
                paymentStatus = PaymentStatus.SENT
        )
    }
}