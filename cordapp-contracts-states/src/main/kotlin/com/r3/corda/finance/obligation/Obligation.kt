package com.r3.corda.finance.obligation

import net.corda.core.contracts.Amount
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.security.PublicKey
import java.time.Instant

/**
 * Obligation Settlement Assumptions
 * ---------------------------------
 *
 * 1. Obligation are _just_ for fiat currency and digital currency for the time being. Support for arbitrary token types
 *    can be added later e.g. shares.
 * 2. Obligations are given a face value in some currency or digital currency e.g. BTC or GBP.
 * 3. Obligations can be settled in a currency or digital currency other than the one specified for the face value.
 *    - If a different currency is used to the face value currency, then a conversion is done for the time the obligation
 *      was first raised.
 * 4. Obligations can be paid down with multiple payments.
 * 5. Obligations can only be paid down with one currency or digital currency type.
 *    - For example, if one currency is initially used, it must be used for the remainder of the payments.
 * 6. The obligee specifies which token types are acceptable for payment on ledger and which settlement rails are appropriate
 *    for off-ledger.
 * 7. Obligations are considered settled when the sum of all payments in the face value currency equal the face value.
 * 8. Obligations are considered in default if they are not fully paid by the dueDate, if one is specified.
 */

@CordaSerializable
data class Payment(
        val accountToPay: Any,
        val paymentReference: PaymentReference?,
        val status: PaymentStatus,
        val amount: Amount<T>,
        val paymentFlow: Class<T>,
        )

@CordaSerializable
sealed class SettlementInstructions {
    data class OnLedger() : SettlementInstructions()
    data class OffLedger(val settlementMethod: List<OffLedgerSettlement<*>>) : SettlementInstructions()
}


// TODO: Do i need to add a settlement currency and an fx rate here?
// Can obligations be settled with any token or does it have to be currency.
// Remember that obligations can be used to settle deliveries of any arbitrary thing, not just cash.

@CordaSerializable
interface SettlementMethod {
    // The public key, account number or whatever, that payment should be made to.
    val beneficiaryAccount: Any
}

// This is an interface because some other custom fields might need to be added.
// It could be the case that some currency conversation is required when the off-ledger payment is made. For example,
// The obligation could be denominated in GBP but the payment could be made in XRP.
interface OffLedgerPayment<T : AbstractMakeOffLedgerPayment> : SettlementMethod {
    // The Oracle used to determine if payment is made.
    val settlementOracle: Party
    // The flow used to initiate the off-ledger payment.
    val paymentFlow: Class<T>
}

// Payment can be made whatever token types the obligee requests. Most likely, the payment will be made in the token
// in which the obligation is denominated. However this might not always be the case. For example, the obligation
// might be denominated in GBP so the obligee accepts GBP from a number of GBP issuers but not all issuers. On the other
// hand, the obligation might be denominated in Alice Ltd    shares
data class OnLedgerSettlement(
        // Payments are always made to public keys on ledger. TODO: Add certificate for AML reasons.
        override val beneficiaryAccount: PublicKey,
        // The type will eventually be a TokenType.
        val settlementTokenType: Any
) : SettlementMethod

data class Obligation<T : Any>(
        // Obligations are always denominated in some token type as they arise on-ledger.
        val faceAmount: Amount<T>,
        // The payer.
        val obligor: AbstractParty,
        // The beneficiary.
        val obligee: AbstractParty,
        // When the obligation should be paid by. May not always be required.
        val dueBy: Instant? = null,
        // Settlement methods the obligee will accept: On ledger, off ledger (crypto, swift, PISP, paypal, etc.).
        // The obligation can be paid in parts. This lists all payments in respect of this obligation and their status.
        // The obligation will be considered settled when all the payments sum up to the face amount of the obligation.
        val settlementMethodsAndPayments: Map<SettlementMethod, List<Payment>> = emptyMap(),
        // Unique identifier for the obligation.
        override val linearId: UniqueIdentifier = UniqueIdentifier()
) : LinearState {
    override val participants: List<AbstractParty> get() = listOf(obligee, obligor)

    // TODO: These should be computed properties.
    // The amount paid in the token type the obligation is denominated in. Note: Payments might be in another currency.
    val amountPaid: Amount<T> = Amount(0L, faceAmount.token),
    // Status: UNSETTLED, SETTLED, PARTIALLY SETTLED, DEFAULTED
    val status: ObligationStatus = ObligationStatus.UNSETTLED,

    fun withSettlementTerms(settlementTerms: SettlementInstructions) = copy(settlementInstructions = settlementTerms)

    fun settle(amount: Amount<T>): Obligation<T> {
        val newAmount = paid + amount
        return when {
            newAmount > faceAmount -> throw IllegalArgumentException("You cannot over pay an obligation.")
            newAmount == faceAmount -> copy(paid = newAmount, status = ObligationStatus.SETTLED)
            // Partial payments are no supported in the rest of the app.
            newAmount < faceAmount -> copy(paid = newAmount, status = ObligationStatus.UNSETTLED)
            else -> throw IllegalStateException("This shouldn't happen!")
        }
    }

    private fun resolveParty(resolver: (AbstractParty) -> Party, abstractParty: AbstractParty): Party {
        return abstractParty as? Party ?: resolver(abstractParty)
    }

    fun withWellKnownIdentities(resolver: (AbstractParty) -> Party): Obligation<T> {
        return copy(obligee = resolveParty(resolver, obligee), obligor = resolveParty(resolver, obligor))
    }

    override fun toString(): String {
        val obligeeString = (obligee as? Party)?.name?.organisation ?: obligee.owningKey.toStringShort()
        val obligorString = (obligor as? Party)?.name?.organisation ?: obligor.owningKey.toStringShort()
        return "ObligationContract($linearId): $obligorString owes $obligeeString $faceAmount ($paid paid)."
    }


}