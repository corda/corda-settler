package com.r3.corda.finance.obligation.oracle.services

import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import com.r3.corda.finance.obligation.contracts.states.Obligation
import com.r3.corda.finance.obligation.oracle.flows.VerifySettlement
import com.r3.corda.finance.ripple.services.XRPClientForVerification
import com.r3.corda.finance.ripple.types.TransactionNotFoundException
import com.r3.corda.finance.ripple.types.XrpPayment
import com.r3.corda.finance.ripple.utilities.hasSucceeded
import com.r3.corda.finance.ripple.utilities.toXRPAmount
import com.r3.corda.lib.tokens.contracts.types.TokenType
import com.typesafe.config.ConfigFactory
import net.corda.core.crypto.SecureHash
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import java.net.URI

@CordaService
class XrpOracleService(val services: AppServiceHub) : SingletonSerializeAsToken() {

    private val configFileName = "xrp.conf"
    private val nodes by lazy { ConfigFactory.parseResources(configFileName).getStringList("nodes").mapNotNull(::URI) }

    private val clientsForVerification = nodes.map { nodeUri -> XRPClientForVerification(nodeUri) }

    /** Check that the last ledger sequence has not passed. */
    private fun isPastLastLedger(payment: XrpPayment<TokenType>): Boolean {
        return clientsForVerification.all { client ->
            client.ledgerIndex().ledgerCurrentIndex > payment.lastLedgerSequence
        }
    }

    private fun checkServersAreUpToDate(): Boolean {
        return clientsForVerification.all { client ->
            val serverState = client.serverState().state.serverState
            serverState in setOf("tracking", "full", "validating", "proposing")
        }
    }

    private fun checkObligeeReceivedPayment(
            xrpPayment: XrpPayment<TokenType>,
            obligation: Obligation<TokenType>
    ): Boolean {
        // Query all the ripple nodes.
        val results = clientsForVerification.map { client ->
            try {
                client.transaction(xrpPayment.paymentReference)
            } catch (e: TransactionNotFoundException) {
                // The transaction is not recognised by the Oracle.
                return false
            } catch (e: MissingKotlinParameterException) {
                // The transaction has no associated metadata yet. In which case, Jackson will not be able to deserialize
                // the response to the TransactionInfoResponse object due to the missing "meta" property.
                if (e.msg.contains("""JSON property meta""")) {
                    return false
                } else {
                    throw e
                }
            }
        }
        // All nodes should report the same result.
        val destinationCorrect = results.all { it.destination.toString() == obligation.settlementMethod?.accountToPay }
        // Using delivered amount instead of amount.
        // See https://developers.ripple.com/partial-payments.html#partial-payments-exploit for further info.
        val amountCorrect = results.all { it.meta.deliveredAmount == xrpPayment.amount.toXRPAmount() }
        val referenceCorrect = results.all { it.invoiceId == SecureHash.sha256(obligation.linearId.id.toString()).toString() }
        val hasSucceeded = results.all { it.hasSucceeded() }
        return destinationCorrect && amountCorrect && referenceCorrect && hasSucceeded
    }

    fun hasPaymentSettled(
            xrpPayment: XrpPayment<TokenType>,
            obligation: Obligation<TokenType>
    ): VerifySettlement.VerifyResult {
        val upToDate = checkServersAreUpToDate()

        if (!upToDate) {
            return VerifySettlement.VerifyResult.PENDING
        }

        val isPastLastLedger = isPastLastLedger(xrpPayment)
        val receivedPayment = checkObligeeReceivedPayment(xrpPayment, obligation)

        return when {
            // Payment received. Boom!
            receivedPayment && !isPastLastLedger -> VerifySettlement.VerifyResult.SUCCESS
            // Return success even if the deadline is passed.
            receivedPayment && isPastLastLedger -> VerifySettlement.VerifyResult.SUCCESS
            // Payment not received. Maybe the reference is wrong or it was sent to the wrong address.
            // This situation will need to be sorted out manually for now...
            !receivedPayment && isPastLastLedger -> VerifySettlement.VerifyResult.TIMEOUT
            // If the deadline is not yet up then we are still pending.
            !receivedPayment && !isPastLastLedger -> VerifySettlement.VerifyResult.PENDING
            else -> throw IllegalStateException("Shouldn't happen!")
        }
    }
}
