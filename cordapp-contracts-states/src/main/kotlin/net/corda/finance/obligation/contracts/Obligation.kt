package net.corda.finance.obligation.contracts

import net.corda.core.contracts.*
import net.corda.core.crypto.toStringShort
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.node.ServiceHub
import net.corda.core.transactions.LedgerTransaction
import net.corda.finance.obligation.types.SettlementInstructions

class Obligation : Contract {

    companion object {
        @JvmStatic
        val CONTRACT_REF: ContractClassName = "net.corda.finance.obligation.contracts.Obligation"
    }

    data class State<T : TokenizableAssetInfo>(
            val amount: Amount<T>,
            val obligor: AbstractParty,
            val obligee: AbstractParty,
            val settlementTerms: SettlementInstructions? = null,
            override val linearId: UniqueIdentifier = UniqueIdentifier()
    ) : LinearState {
        override val participants: List<AbstractParty> get() = listOf(obligee, obligor)

        fun withSettlementTerms(settlementTerms: SettlementInstructions) = copy(settlementTerms = settlementTerms)

        private fun resolveParty(services: ServiceHub, abstractParty: AbstractParty): Party {
            return abstractParty as? Party ?: services.identityService.requireWellKnownPartyFromAnonymous(abstractParty)
        }

        fun withWellKnownIdentities(services: ServiceHub): State<T> {
            return copy(obligee = resolveParty(services, obligee), obligor = resolveParty(services, obligor))
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