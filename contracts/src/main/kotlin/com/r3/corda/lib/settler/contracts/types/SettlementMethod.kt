package com.r3.corda.lib.settler.contracts.types

import com.r3.corda.lib.obligation.contracts.types.SettlementMethod
import com.r3.corda.lib.settler.api.AbstractMakeOffLedgerPayment
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.identity.Party
import java.security.PublicKey

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