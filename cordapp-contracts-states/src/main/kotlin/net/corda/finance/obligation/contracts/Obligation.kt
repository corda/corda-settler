package net.corda.finance.obligation.contracts

import net.corda.core.contracts.*
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.transactions.LedgerTransaction
import net.corda.finance.obligation.types.SettlementInstructions

class Obligation : Contract {

    companion object {
        @JvmStatic
        val CONTRACT_REF: ContractClassName = "net.corda.finance.obligation.contracts.Obligation"
    }

    data class State<T : Any>(
            val amount: Amount<T>,
            val obligor: AbstractParty,
            val obligee: AbstractParty,
            val settlementInstructions: SettlementInstructions? = null,
            override val linearId: UniqueIdentifier = UniqueIdentifier()
    ) : LinearState {
        override val participants: List<AbstractParty> get() = listOf(obligee, obligor)

        fun withSettlementTerms(settlementTerms: SettlementInstructions) = copy(settlementInstructions = settlementTerms)

        private fun resolveParty(resolver: (AbstractParty) -> Party, abstractParty: AbstractParty): Party {
            return abstractParty as? Party ?: resolver(abstractParty)
        }

        fun withWellKnownIdentities(resolver: (AbstractParty) -> Party): State<T> {
            return copy(obligee = resolveParty(resolver, obligee), obligor = resolveParty(resolver, obligor))
        }

        override fun toString(): String {
            val obligeeString = (obligee as? Party)?.name?.organisation ?: obligee.owningKey.toStringShort()
            val obligorString = (obligor as? Party)?.name?.organisation ?: obligor.owningKey.toStringShort()
            return "Obligation($linearId): $obligorString owes $obligeeString $amount."
        }
    }

    interface Commands {
        class Create : TypeOnlyCommandData()
        class AddSettlementTerms : TypeOnlyCommandData()
        class AddPaymentDetails : TypeOnlyCommandData()
        class Extinguish : TypeOnlyCommandData()
    }

    override fun verify(tx: LedgerTransaction) = Unit
}