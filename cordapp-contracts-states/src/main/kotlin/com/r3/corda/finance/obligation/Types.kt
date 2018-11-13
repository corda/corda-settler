package com.r3.corda.finance.obligation

import com.r3.corda.finance.obligation.flows.AbstractMakeOffLedgerPayment
import net.corda.core.contracts.TokenizableAssetInfo
import net.corda.core.identity.Party
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

@CordaSerializable
enum class ObligationStatus { UNSETTLED, SETTLED, DEFAULT }

typealias PaymentReference = String

@CordaSerializable
enum class PaymentStatus { NOT_SENT, SENT, ACCEPTED }

/** Generic terms which provide a day by when the obligation must be settled. */
@CordaSerializable
interface SettlementInstructions

/** Terms specific to on-ledger settlement. */
open class OnLedgerSettlementTerms : SettlementInstructions

/**
 * Terms specific to off-ledger settlement. Here some kind of account must be specified. The account might be in the
 * form of a bank account number or a crypto currency address.
 */
interface OffLedgerSettlementInstructions<T : AbstractMakeOffLedgerPayment> : SettlementInstructions {
    val accountToPay: Any
    val settlementOracle: Party
    val paymentFlow: Class<T>
    val paymentStatus: PaymentStatus
    val paymentReference: PaymentReference?

    fun addPaymentReference(ref: PaymentReference): OffLedgerSettlementInstructions<T>
}