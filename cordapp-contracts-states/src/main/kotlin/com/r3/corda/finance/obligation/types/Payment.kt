package com.r3.corda.finance.obligation.types

import net.corda.core.contracts.Amount
import net.corda.core.serialization.CordaSerializable

typealias PaymentReference = String

@CordaSerializable
interface Payment<T : Money> {
    /** Reference given to off-ledger payment by settlement rail. */
    val paymentReference: PaymentReference
    /** Amount that was paid in the required token type. */
    val amount: Amount<T>
    /** SENT, ACCEPTED or FAILED. */
    var status: PaymentStatus
}

@CordaSerializable
enum class PaymentStatus { SETTLED, SENT, FAILED }