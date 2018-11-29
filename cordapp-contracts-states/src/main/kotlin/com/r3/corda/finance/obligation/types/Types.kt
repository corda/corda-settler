package com.r3.corda.finance.obligation.types

import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import java.time.Instant

@CordaSerializable
sealed class SettlementOracleResult {
    data class Success(val stx: SignedTransaction) : SettlementOracleResult()
    data class Failure(val stx: SignedTransaction?, val message: String) : SettlementOracleResult()
}

@CordaSerializable
data class FxRateRequest(val baseCurrency: Money, val counterCurrency: Money, val time: Instant)

typealias FxRateResponse = FxRate