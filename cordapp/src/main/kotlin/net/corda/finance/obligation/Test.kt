package net.corda.finance.obligation

import com.ripple.core.coretypes.AccountID
import com.ripple.core.coretypes.Amount
import com.ripple.core.coretypes.uint.UInt32
import com.ripple.core.types.known.tx.txns.Payment

const val secret = "sasKgJbTbka3ahFew2BZybfNg494C"
const val account = "ra6mzL1Xy9aN5eRdjzn9CHTMwcczG1uMpN"
const val destination = "rNmkj4AtjEHJh3D9hMRC4rS3CXQ9mX4S4b"
const val sequence = "1"
const val amount = "10000"
const val fee = "1000"

fun main(args: Array<String>) {
    val from = AccountID.fromString(account)
    val to = AccountID.fromString(destination)
    val amount = Amount.fromString(amount)
    val sequence = UInt32(sequence)
    val fee = Amount.fromString(fee)

    val payment = Payment().apply {
        account(from)
        destination(to)
        amount(amount)
        sequence(sequence)
        fee(fee)
    }

    val signed = payment.sign(secret)
    println(signed.tx_blob)
}


/*
Address     ra6mzL1Xy9aN5eRdjzn9CHTMwcczG1uMpN
Secret      sasKgJbTbka3ahFew2BZybfNg494C
Balance     10,000 XRP

Address     rNmkj4AtjEHJh3D9hMRC4rS3CXQ9mX4S4b
Secret      ssn8cYYksFFexYq97sw9UnvLnMKgh
Balance     10,000 XRP
 */