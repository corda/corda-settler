package com.r3.corda.finance.obligation.contracts.types

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.r3.corda.finance.obligation.contracts.flows.AbstractMakeOffLedgerPayment
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.security.PublicKey
import java.time.Instant

/** All settlement methods require some key or account that a payment must be made to. */
@CordaSerializable
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "_type")
interface SettlementMethod {
    /** The public key, account number or whatever, that payment should be made to. */
    val accountToPay: Any
}

/**
 * A simple fx rate type.
 * TODO: Replace this with a proper fx library.
 */
@CordaSerializable
data class FxRate(val baseCurrency: TokenType, val counterCurrency: TokenType, val time: Instant, val rate: Number)

/**
 * This is an interface because some other custom fields might need to be added.
 * It could be the case that some currency conversation is required when the off-ledger payment is made. For example,
 * The obligation could be denominated in GBP but the payment could be made in XRP.
 */
interface OffLedgerPayment<T : AbstractMakeOffLedgerPayment> : SettlementMethod {
    /** The Oracle used to determine if payment is made. Use null for manual payment verification */
    val settlementOracle: Party?
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
        val acceptableTokenTypes: List<TokenType>
) : SettlementMethod