package net.corda.finance.obligation.types

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
enum class PaymentStatus { NOT_SENT, SENT, ACCEPTED, REJECTED }