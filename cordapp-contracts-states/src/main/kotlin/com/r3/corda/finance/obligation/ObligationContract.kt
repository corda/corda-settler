package com.r3.corda.finance.obligation

import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractClassName
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.transactions.LedgerTransaction

class ObligationContract : Contract {

    companion object {
        @JvmStatic
        val CONTRACT_REF: ContractClassName = "com.r3.corda.finance.obligation.ObligationContract"
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