package com.r3.corda.finance.obligation.workflows

import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

class DummyContract : Contract {
    override fun verify(tx: LedgerTransaction) = Unit
}