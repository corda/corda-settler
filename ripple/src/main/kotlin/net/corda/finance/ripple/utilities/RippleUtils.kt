package net.corda.finance.ripple.utilities

import com.ripple.core.coretypes.hash.Hash256
import net.corda.core.contracts.Amount
import net.corda.core.crypto.SecureHash
import net.corda.finance.ripple.types.TransactionInfoResponse

fun TransactionInfoResponse.hasSucceeded() = status == "tesSuccess" && validated

fun Amount<*>.toRippleAmount(): com.ripple.core.coretypes.Amount = com.ripple.core.coretypes.Amount.fromString(quantity.toString())

fun SecureHash.toRippleHash(): Hash256 = Hash256.fromHex(toString())

val DEFAULT_RIPPLE_FEE = com.ripple.core.coretypes.Amount.fromString("1000")