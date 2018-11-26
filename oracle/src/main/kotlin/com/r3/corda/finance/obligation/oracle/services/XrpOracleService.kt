package com.r3.corda.finance.obligation.oracle.services

import com.r3.corda.finance.obligation.types.DigitalCurrency
import com.r3.corda.finance.obligation.oracle.flows.VerifySettlement
import com.r3.corda.finance.obligation.states.Obligation
import com.r3.corda.finance.ripple.services.XRPClientForVerification
import com.r3.corda.finance.ripple.types.TransactionNotFoundException
import com.r3.corda.finance.ripple.types.XrpPayment
import com.r3.corda.finance.ripple.utilities.hasSucceeded
import com.r3.corda.finance.ripple.utilities.toXRPAmount
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
    private fun isPastLastLedger(payment: XrpPayment<DigitalCurrency>): Boolean {
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
            xrpPayment: XrpPayment<DigitalCurrency>,
            obligation: Obligation<DigitalCurrency>
    ): Boolean {
        // Query all the ripple nodes.
        val results = clientsForVerification.map { client ->
            try {
                client.transaction(xrpPayment.paymentReference)
            } catch (e: TransactionNotFoundException) {
                // The transaction is not recognised by the Oracle.
                return false
            }
        }
        // All nodes should report the same result.
        val destinationCorrect = results.all { it.destination == obligation.settlementMethod?.accountToPay }
        val amountCorrect = results.all { it.amount == xrpPayment.amount.toXRPAmount() }
        val referenceCorrect = results.all { it.invoiceId == SecureHash.sha256(obligation.linearId.id.toString()).toString() }
        val hasSucceeded = results.all { it.hasSucceeded() }
        return destinationCorrect && amountCorrect && referenceCorrect && hasSucceeded
    }

    fun hasPaymentSettled(
            xrpPayment: XrpPayment<DigitalCurrency>,
            obligation: Obligation<DigitalCurrency>
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
