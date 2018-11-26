package com.r3.corda.finance.obligation.types

import net.corda.core.contracts.TokenizableAssetInfo
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import java.math.BigDecimal
import java.time.Instant
import java.util.*

/** A common interface for things which are fungible. */
@CordaSerializable
interface Fungible : TokenizableAssetInfo {
    val symbol: String
    val description: String
}

/** A common interface for all money-like states. */
interface Money : Fungible

/** A wrapper for currency as it doesn't implement our interfaces and adds specificity around the currency being fiat. */
data class FiatCurrency(private val currency: Currency) : Money {
    override val symbol: String get() = currency.symbol
    override val description: String get() = currency.displayName
    override val displayTokenSize: BigDecimal get() = BigDecimal.ONE.scaleByPowerOfTen(-currency.defaultFractionDigits)
    override fun toString(): String = symbol
}

/** A representation of digital currency. This implementation somewhat mirrors that of [Currency]. */
data class DigitalCurrency(
        override val symbol: String,
        override val description: String,
        private val defaultFractionDigits: Int = 0
) : Money {
    override val displayTokenSize: BigDecimal get() = BigDecimal.ONE.scaleByPowerOfTen(-defaultFractionDigits)

    companion object {
        private val registry = mapOf(Pair("XRP", DigitalCurrency("XRP", "Ripple", 6)))
        fun getInstance(currencyCode: String): DigitalCurrency {
            return registry[currencyCode] ?: throw IllegalArgumentException("$currencyCode doesn't exist.")
        }
    }

    override fun toString() = symbol
}

