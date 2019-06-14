package com.r3.corda.finance.obligation.contracts.states

import com.r3.corda.finance.obligation.contracts.ObligationContract
import com.r3.corda.finance.obligation.contracts.types.Payment
import com.r3.corda.finance.obligation.contracts.types.PaymentStatus
import com.r3.corda.finance.obligation.contracts.types.SettlementMethod
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.time.Instant

/**
 * Obligation Settlement Assumptions:
 *
 * 1. Obligation are just for fiat currency and digital currency for the time being. Support for arbitrary token states
 *    can be added later e.g. shares.
 * 2. Obligations are given a face value in some currency or digital currency e.g. BTC or GBP.
 * 3. Obligations can be settled in a currency or digital currency other than the one specified for the face value.
 *    - If a different currency is used to the face value currency, then a conversion is done for the time the
 *      obligation was first raised.
 *    - This process requires a novation of the obligation from one currency to another.
 * 4. Obligations can be paid down with multiple payments.
 * 5. Obligations can only be paid down with one currency or digital currency type.
 *    - For example, if one currency is initially used, it must be used for the remainder of the payments.
 * 6. The obligee specifies which token states are acceptable for payment on ledger and which settlement rails are
 *    appropriate for off-ledger.
 *    - Only one settlement method can be supplied at any one time.
 * 7. Obligations are considered settled when the sum of all payments in the face value currency equal the face value.
 * 8. Obligations are considered in default if they are not fully paid by the dueDate, if one is specified.
 *
 */

@BelongsToContract(ObligationContract::class)
data class Obligation<T : TokenType>(
        /** Obligations are always denominated in some token type as we need a reference for FX purposes. */
        val faceAmount: Amount<T>,
        /** The payer. Can be pseudo-anonymous. */
        val obligor: AbstractParty,
        /** The beneficiary. Can be pseudo-anonymous. */
        val obligee: AbstractParty,
        /** When the obligation should be paid by. May not always be required. */
        val dueBy: Instant? = null,
        /** The time when the obligation was created. */
        val createdAt: Instant = Instant.now(),
        /** Settlement methods the obligee will accept: On ledger, off ledger (crypto, swift, PISP, paypal, etc.). */
        val settlementMethod: SettlementMethod? = null,
        /** The obligation can be paid in parts. This lists all payments in respect of this obligation */
        val payments: List<Payment<T>> = emptyList(),
        /** Unique identifier for the obligation. */
        override val linearId: UniqueIdentifier = UniqueIdentifier()
) : LinearState {

    @CordaSerializable
    enum class SettlementStatus { UNSETTLED, SETTLED, PARTIALLY_SETTLED }

    /** Always returns the obligor and obligee. */
    override val participants: List<AbstractParty> get() = listOf(obligee, obligor)

    /** The sum of amounts for all payments. */
    val amountPaid: Amount<T>
        get() = payments
                .filter { it.status == PaymentStatus.SETTLED }
                .map { it.amount }
                .fold(Amount.zero(faceAmount.token)) { acc, amount -> acc + amount }

    /** A defaulted obligation is one where the current time is greater than the [dueBy] time. */
    val inDefault: Boolean get() = dueBy?.let { Instant.now() > dueBy } ?: false

    /** Returns the current state of the obligation. */
    val settlementStatus: SettlementStatus
        get() {
            return when {
                payments.isEmpty() -> SettlementStatus.UNSETTLED
                payments.isNotEmpty() && amountPaid < faceAmount -> SettlementStatus.PARTIALLY_SETTLED
                payments.isNotEmpty() && amountPaid == faceAmount -> SettlementStatus.SETTLED
                else -> throw IllegalStateException("Shouldn't reach here.")
            }
        }

    /** For adding or changing the settlement method. */
    fun withSettlementMethod(settlementMethod: SettlementMethod?): Obligation<T> {
        return if (settlementStatus != SettlementStatus.UNSETTLED) {
            throw IllegalStateException("Cannot change settlement method after a payment has been made.")
        } else copy(settlementMethod = settlementMethod)
    }

    /** Update the due by date. */
    fun withDueByDate(dueBy: Instant) = copy(dueBy = dueBy)

    /** Update the due by date. */
    fun withNewCounterparty(oldParty: AbstractParty, newParty: AbstractParty): Obligation<T> {
        return when {
            obligee == oldParty -> copy(obligee = newParty)
            obligor == oldParty -> copy(obligor = newParty)
            else -> throw IllegalArgumentException("The oldParty is not recognised as a participant in this obligation.")
        }
    }

    fun <U : TokenType> withNewFaceValueToken(newAmount: Amount<U>): Obligation<U> {
        return if (payments.isEmpty()) {
            Obligation(newAmount, obligor, obligee, dueBy, createdAt, settlementMethod, emptyList(), linearId)
        } else {
            throw IllegalStateException("The faceValue token type cannot be updated after payments have been made.")
        }
    }

    /** Update the amount, keeping the token type the same. */
    fun withNewFaceValueQuantity(newAmount: Amount<T>): Obligation<T> {
        return if (newAmount < amountPaid) {
            throw IllegalStateException("Can't reduce the faceAmount to less than the current amountPaid.")
        } else copy(faceAmount = newAmount)
    }

    /** Add a new payment to the payments list. */
    fun withPayment(payment: Payment<T>): Obligation<T> {
        val newAmountPaid = amountPaid + payment.amount
        return if (newAmountPaid > faceAmount) {
            throw IllegalStateException("You cannot over pay an obligation.")
        } else {
            copy(payments = payments + payment)
        }
    }

    private fun resolveParty(resolver: (AbstractParty) -> Party, abstractParty: AbstractParty): Party {
        return abstractParty as? Party ?: resolver(abstractParty)
    }

    /** Returns the obligation with well known identities. */
    fun withWellKnownIdentities(resolver: (AbstractParty) -> Party): Obligation<T> {
        return copy(obligee = resolveParty(resolver, obligee), obligor = resolveParty(resolver, obligor))
    }

    override fun toString(): String {
        val obligeeString = (obligee as? Party)?.name?.organisation
                ?: obligee.owningKey.toStringShort().substring(0, 10)
        val obligorString = (obligor as? Party)?.name?.organisation
                ?: obligor.owningKey.toStringShort().substring(0, 10)
        val settlementMethod = if (settlementMethod == null) "No settlement method added" else settlementMethod.toString()
        var paymentString = ""
        if (payments.isNotEmpty()) {
            payments.forEach { paymentString += "\n\t\t\t$it" }
        } else {
            paymentString = "\n\t\t\tNo payments made."
        }
        return "Obligation($linearId): $obligorString owes $obligeeString $faceAmount ($amountPaid paid)." +
                "\n\t\tSettlement status: $settlementStatus" +
                "\n\t\tSettlementMethod: $settlementMethod" +
                "\n\t\tPayments:" +
                paymentString
    }

}