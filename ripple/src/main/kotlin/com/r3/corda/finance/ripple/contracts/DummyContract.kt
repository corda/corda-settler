package com.r3.corda.finance.ripple.contracts

import net.corda.core.contracts.Contract
import net.corda.core.transactions.LedgerTransaction

// This is required so that Corda picks up this JAR as a CorDapp dependency.
// TODO: Remove this when Corda 4.1 is released.
class DummyContract : Contract {
    override fun verify(tx: LedgerTransaction) {

    }
}