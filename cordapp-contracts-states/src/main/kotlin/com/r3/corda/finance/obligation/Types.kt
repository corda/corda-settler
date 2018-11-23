package com.r3.corda.finance.obligation

import com.r3.corda.finance.obligation.flows.AbstractMakeOffLedgerPayment
import net.corda.core.contracts.Amount
import net.corda.core.contracts.TokenizableAssetInfo
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import java.math.BigDecimal
import java.security.PublicKey
import java.util.*

/** All settlement methods require some key or account that a payment must be made to. */
@CordaSerializable
interface SettlementMethod {
    /** The public key, account number or whatever, that payment should be made to. */
    val accountToPay: Any
}

/**
 * This is an interface because some other custom fields might need to be added.
 * It could be the case that some currency conversation is required when the off-ledger payment is made. For example,
 * The obligation could be denominated in GBP but the payment could be made in XRP.
 */
interface OffLedgerPayment<T : AbstractMakeOffLedgerPayment> : SettlementMethod {
    /** The Oracle used to determine if payment is made. */
    val settlementOracle: Party
    /** The flow used to initiate the off-ledger payment. */
    val paymentFlow: Class<T>
}

/**
 * Payment can be made whatever token states the obligee requests. Most likely, the payment will be made in the token
 * in which the obligation is denominated. However this might not always be the case. For example, the obligation
 * might be denominated in GBP so the obligee accepts GBP from a number of GBP issuers but not all issuers. On the other
 * hand, the obligation might be denominated GBP but also accepts payments in some other on-ledger currency. As such it
 * might be the case that some currency conversion is required.
 */
data class OnLedgerSettlement(
        /** Payments are always made to public keys on ledger. TODO: Add certificate for AML reasons. */
        override val accountToPay: PublicKey,
        /** The type will eventually be a TokenType. */
        val acceptableTokenTypes: List<Money>
) : SettlementMethod

typealias PaymentReference = String

@CordaSerializable
interface Payment<T : Any> {
    /** Reference given to off-ledger payment by settlement rail. */
    val paymentReference: PaymentReference
    /** Amount that was paid in the required token type. */
    val amount: Amount<T>
    /** SENT, ACCEPTED or FAILED. */
    var status: PaymentStatus
}

@CordaSerializable
enum class PaymentStatus { SETTLED, SENT, FAILED }

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

@CordaSerializable
sealed class SettlementOracleResult {
    data class Success(val stx: SignedTransaction) : SettlementOracleResult()
    data class Failure(val stx: SignedTransaction, val message: String) : SettlementOracleResult()
}

@CordaSerializable
data class FxRateRequest(val baseCurrency: Money, val foreignCurrency: Money)

@CordaSerializable
data class FxRateResponse(val fxRate: Number)