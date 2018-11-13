package com.r3.corda.finance.obligation.oracle.services

import com.r3.corda.finance.obligation.PaymentStatus
import com.r3.corda.finance.obligation.contracts.Obligation
import com.r3.corda.finance.obligation.oracle.flows.VerifySettlement
import com.r3.corda.finance.ripple.services.XRPClientForVerification
import com.r3.corda.finance.ripple.types.XRPSettlementInstructions
import com.r3.corda.finance.ripple.utilities.hasSucceeded
import com.r3.corda.finance.ripple.utilities.toXRPAmount
import com.typesafe.config.ConfigFactory
import net.corda.core.crypto.SecureHash
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import java.net.URI

const val configFileName = "xrp.conf"

@CordaService
class XrpOracleService(val services: AppServiceHub) : SingletonSerializeAsToken() {
    private val nodes by lazy { ConfigFactory.parseResources(configFileName).getStringList("nodes").mapNotNull(::URI) }
    private val clientsForVerification = nodes.map { nodeUri -> XRPClientForVerification(nodeUri) }

    fun hasPaymentSettled(settlementInstructions: XRPSettlementInstructions, obligation: Obligation.State<*>): VerifySettlement.VerifyResult {
        val notSent = settlementInstructions.paymentStatus == PaymentStatus.NOT_SENT

        // Check that all nodes are up to date.
        val upToDate = clientsForVerification.all { client ->
            val serverState = client.serverState().state.serverState
            serverState in setOf("tracking", "full", "validating", "proposing")
        }

        // Check that the last ledger sequence has not passed.
        val isPastLastLedger = clientsForVerification.all { client ->
            client.ledgerIndex().ledgerCurrentIndex > settlementInstructions.lastLedgerSequence
        }

        return when {
            // Payment hasn't even been sent, so this is definitely a timeout.
            notSent && isPastLastLedger -> VerifySettlement.VerifyResult.TIMEOUT
            // Payment not sent but there is still time.
            notSent && !isPastLastLedger -> VerifySettlement.VerifyResult.PENDING
            // Payment HAS been sent.
            !notSent -> {
                // Get the payment reference.
                val paymentReference = settlementInstructions.paymentReference
                        ?: throw IllegalStateException("No transaction hash has been specified yet.")
                // The oracle can (and probably should) use more than one node to verify payment occurred.
                val results = clientsForVerification.map { client -> client.transaction(paymentReference) }
                val destinationCorrect = results.all { it.destination == settlementInstructions.accountToPay }
                val amountCorrect = results.all { it.amount == obligation.faceAmount.toXRPAmount() }
                val referenceCorrect = results.all { it.invoiceId == SecureHash.sha256(obligation.linearId.id.toString()).toString() }
                val hasSucceeded = results.all { it.hasSucceeded() }
                val hasReceived = destinationCorrect && amountCorrect && referenceCorrect && hasSucceeded && upToDate

                when {
                    hasReceived && !isPastLastLedger -> VerifySettlement.VerifyResult.SUCCESS
                    // Return success even if the deadline is passed.
                    hasReceived && isPastLastLedger -> VerifySettlement.VerifyResult.SUCCESS
                    // Allow a grace period for payments which have been instructed but not yet confirmed.
                    !hasReceived && isPastLastLedger -> VerifySettlement.VerifyResult.PENDING
                    // If the deadline is not yet up then we are still pending.
                    !hasReceived && !isPastLastLedger -> VerifySettlement.VerifyResult.PENDING
                    else -> throw IllegalStateException("Shouldn't happen!")
                }
            }
            else -> throw IllegalStateException("Shouldn't happen!")
        }
    }
}
