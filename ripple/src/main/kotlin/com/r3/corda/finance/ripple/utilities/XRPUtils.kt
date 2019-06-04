package com.r3.corda.finance.ripple.utilities

import com.r3.corda.finance.ripple.types.TransactionInfoResponse
import com.ripple.core.coretypes.hash.Hash256
import net.corda.core.contracts.Amount
import net.corda.core.crypto.SecureHash
import com.ripple.core.coretypes.Amount as XRPAmount

fun TransactionInfoResponse.hasSucceeded() = status == "success" && validated

fun Amount<*>.toXRPAmount(): XRPAmount = XRPAmount.fromString(quantity.toString())

fun Int.toXRPAmount(): XRPAmount = XRPAmount.fromString(toString())

fun SecureHash.toXRPHash(): Hash256 = Hash256.fromHex(toString())

val DEFAULT_XRP_FEE = XRPAmount.fromString("1000")
