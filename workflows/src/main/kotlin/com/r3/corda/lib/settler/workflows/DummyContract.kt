package com.r3.corda.lib.settler.workflows

import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

class DummyContract : Contract {
    override fun verify(tx: LedgerTransaction) = Unit
}