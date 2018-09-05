package net.corda.finance.obligation

import net.corda.core.contracts.Amount
import net.corda.core.contracts.TokenizableAssetInfo
import net.corda.core.serialization.CordaSerializable
import net.corda.finance.AMOUNT
import java.math.BigDecimal

@CordaSerializable
data class DigitalCurrency(
        val commodityCode: String,
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

@JvmField
val XRP: DigitalCurrency = DigitalCurrency.getInstance("XRP")

fun RIPPLES(amount: Int): Amount<DigitalCurrency> = AMOUNT(amount, XRP)
fun RIPPLES(amount: Long): Amount<DigitalCurrency> = AMOUNT(amount, XRP)
fun RIPPLES(amount: Double): Amount<DigitalCurrency> = AMOUNT(amount, XRP)

val Int.XRP: Amount<DigitalCurrency> get() = RIPPLES(this)
val Long.XRP: Amount<DigitalCurrency> get() = RIPPLES(this)
val Double.XRP: Amount<DigitalCurrency> get() = RIPPLES(this)