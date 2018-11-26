package com.r3.corda.finance.obligation.contracts

import net.corda.core.contracts.Contract
import net.corda.core.contracts.ContractClassName
import net.corda.core.transactions.LedgerTransaction

class ObligationContract : Contract {

    companion object {
        @JvmStatic
        val CONTRACT_REF: ContractClassName = "com.r3.corda.finance.obligation.contracts.ObligationContract"
    }

    // TODO: Write contract code.
    override fun verify(tx: LedgerTransaction) = Unit
}