package net.corda.finance.ripple.utilities

import net.corda.finance.ripple.types.TransactionInfoResponse

fun TransactionInfoResponse.hasSucceeded() = status == "tesSuccess" && validated