package com.r3.corda.lib.settler.contracts.types

import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction

@CordaSerializable
sealed class SettlementOracleResult {
    data class Success(val stx: SignedTransaction) : SettlementOracleResult()
    data class Failure(val stx: SignedTransaction?, val message: String) : SettlementOracleResult()
}