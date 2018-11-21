package com.r3.corda.finance.obligation

import net.corda.core.contracts.TokenizableAssetInfo
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import java.math.BigDecimal

interface Currency : TokenizableAssetInfo

data class FiatCurrency(override val of: Currency) : Currency {
    override val symbol: String get() = of.symbol
    override val name: String get() = of.displayName
    override val displayTokenSize: BigDecimal get() = BigDecimal.ONE.scaleByPowerOfTen(-of.defaultFractionDigits)
}

@CordaSerializable
data class DigitalCurrency(
        val currencyCode: String,
        val displayName: String,
        val defaultFractionDigits: Int = 0
) : TokenizableAssetInfo {
    override val displayTokenSize: BigDecimal get() = BigDecimal.ONE.scaleByPowerOfTen(-defaultFractionDigits)

    companion object {
        private val registry = mapOf(Pair("XRP", DigitalCurrency("XRP", "Ripple", 6)))
        fun getInstance(currencyCode: String): DigitalCurrency {
            return registry[currencyCode] ?: throw IllegalArgumentException("$currencyCode doesn't exist.")
        }
    }
}

@CordaSerializable
sealed class OracleResult {
    data class Success(val stx: SignedTransaction) : OracleResult()
    data class Failure(val message: String) : OracleResult()
}

@CordaSerializable
enum class ObligationStatus { UNSETTLED, SETTLED, DEFAULT }

typealias PaymentReference = String

@CordaSerializable
enum class PaymentStatus { NOT_SENT, SENT, ACCEPTED }

///** Generic terms which provide a day by when the obligation must be settled. */
//@CordaSerializable
//interface SettlementInstructions
//
///** Terms specific to on-ledger settlement. */
//open class OnLedgerSettlementTerms : SettlementInstructions
//
///**
// * Terms specific to off-ledger settlement. Here some kind of account must be specified. The account might be in the
// * form of a bank account number or a crypto currency address.
// */
//interface OffLedgerSettlementInstructions<T : AbstractMakeOffLedgerPayment> : SettlementInstructions {
//    val accountToPay: Any
//    val settlementOracle: Party
//    val paymentFlow: Class<T>
//    val paymentStatus: PaymentStatus
//    val paymentReference: PaymentReference?
//
//    fun addPaymentReference(ref: PaymentReference): OffLedgerSettlementInstructions<T>
//}