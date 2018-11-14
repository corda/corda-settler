package net.corda.finance.obligation.types

import net.corda.core.contracts.TokenizableAssetInfo
import net.corda.core.serialization.CordaSerializable
import java.math.BigDecimal

@CordaSerializable
data class DigitalCurrency(
        val currencyCode: String,
        val displayName: String,
        val defaultFractionDigits: Int = 0
) : TokenizableAssetInfo {
    override val displayTokenSize: BigDecimal get() = BigDecimal.ONE.scaleByPowerOfTen(-defaultFractionDigits)

    companion object {
        private val registry = mapOf(Pair("XRP", DigitalCurrency("XRP", "Ripple")))
        fun getInstance(currencyCode: String): DigitalCurrency {
            return registry[currencyCode] ?: throw IllegalArgumentException("$currencyCode doesn't exist.")
        }
    }
}

