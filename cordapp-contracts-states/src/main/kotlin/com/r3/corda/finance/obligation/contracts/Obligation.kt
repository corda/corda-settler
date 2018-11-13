package com.r3.corda.finance.obligation.contracts

import com.r3.corda.finance.obligation.ObligationStatus
import com.r3.corda.finance.obligation.SettlementInstructions
import net.corda.core.contracts.*
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction

class Obligation : Contract {

    companion object {
        @JvmStatic
        val CONTRACT_REF: ContractClassName = "com.r3.corda.finance.obligation.contracts.Obligation"
    }

    data class State<T : Any>(
            val faceAmount: Amount<T>,
            val obligor: AbstractParty,
            val obligee: AbstractParty,
            val paid: Amount<T> = Amount(0L, faceAmount.token),
            val settlementInstructions: SettlementInstructions? = null,
            val status: ObligationStatus = ObligationStatus.UNSETTLED,
            override val linearId: UniqueIdentifier = UniqueIdentifier()
    ) : LinearState {
        override val participants: List<AbstractParty> get() = listOf(obligee, obligor)

        fun withSettlementTerms(settlementTerms: SettlementInstructions) = copy(settlementInstructions = settlementTerms)

        fun settle(amount: Amount<T>): Obligation.State<T> {
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

        fun withWellKnownIdentities(resolver: (AbstractParty) -> Party): State<T> {
            return copy(obligee = resolveParty(resolver, obligee), obligor = resolveParty(resolver, obligor))
        }

        override fun toString(): String {
            val obligeeString = (obligee as? Party)?.name?.organisation ?: obligee.owningKey.toStringShort()
            val obligorString = (obligor as? Party)?.name?.organisation ?: obligor.owningKey.toStringShort()
            return "Obligation($linearId): $obligorString owes $obligeeString $faceAmount ($paid paid)."
        }
    }

    interface Commands {
        class Create : TypeOnlyCommandData()
        class AddSettlementTerms : TypeOnlyCommandData()
        class AddPaymentDetails : TypeOnlyCommandData()
        class Extinguish : TypeOnlyCommandData()
    }

    // TODO: Write contract code.
    override fun verify(tx: LedgerTransaction) = Unit
}