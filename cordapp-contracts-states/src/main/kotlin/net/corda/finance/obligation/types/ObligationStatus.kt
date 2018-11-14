package net.corda.finance.obligation.types

import net.corda.core.serialization.CordaSerializable

@CordaSerializable
enum class ObligationStatus { UNSETTLED, SETTLED, DEFAULT }