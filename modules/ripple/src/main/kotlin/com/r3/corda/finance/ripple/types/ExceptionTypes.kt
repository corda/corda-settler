package com.r3.corda.finance.ripple.types

/** Exceptions. */

open class RippleRpcException : Exception()

class AccountNotFoundException : RippleRpcException() {
    override fun toString() = "Account ID not found."
}

class TransactionNotFoundException : RippleRpcException() {
    override fun toString() = "Transaction ID not found."
}

class IncorrectSequenceNumberException : RippleRpcException() {
    override fun toString() = "The sequence number is incorrect. " +
            "It is likely that the same transaction has been submitted twice."
}

class InsufficientBalanceException : RippleRpcException() {
    override fun toString() = "You don't have enough XRP to make the payment!"
}

class AlreadysubmittedException : RippleRpcException() {
    override fun toString() = "The transaction has already been submitted."
}

class PaymentToSelfException : RippleRpcException() {
    override fun toString() = "The payment is being made from and to the same account."
}