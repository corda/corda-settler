package net.corda.finance.obligation.types

import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.finance.obligation.flows.MakeOffLedgerPayment

/** Generic terms which provide a day by when the obligation must be settled. */
@CordaSerializable
interface SettlementInstructions

/** Terms specific to on-ledger settlement. */
interface OnLedgerSettlementTerms : SettlementInstructions

/**
 * Terms specific to off-ledger settlement. Here some kind of account must be specified. The account might be in the
 * form of a bank account number or a crypto currency address.
 */
interface OffLedgerSettlementTerms<T : MakeOffLedgerPayment> : SettlementInstructions {
    val accountToPay: Any
    val settlementOracle: Party
    val paymentFlow: T
}