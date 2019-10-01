package com.r3.corda.lib.settler.contracts

import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

class Dummycontract : Contract {
    override fun verify(tx: LedgerTransaction) {
        // Exists only so that Corda class loads the types in this JAR.
    }
}