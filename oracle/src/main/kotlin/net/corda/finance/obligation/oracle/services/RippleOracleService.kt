package net.corda.finance.obligation.oracle.services

import com.typesafe.config.ConfigFactory
import net.corda.core.crypto.SecureHash
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.finance.obligation.contracts.Obligation
import net.corda.finance.obligation.oracle.flows.VerifySettlement
import net.corda.finance.ripple.XRPClientForVerification
import net.corda.finance.ripple.types.XRPSettlementInstructions
import net.corda.finance.ripple.utilities.hasSucceeded
import net.corda.finance.ripple.utilities.toXRPAmount
import java.net.URI

const val configFileName = "xrp.conf"

@CordaService
class RippleOracleService(val services: AppServiceHub) : SingletonSerializeAsToken() {
    private val nodes by lazy { ConfigFactory.parseResources(configFileName).getStringList("nodes").mapNotNull(::URI) }
    private val clientsForVerification = nodes.map { nodeUri -> XRPClientForVerification(nodeUri) }

    fun hasPaymentSettled(settlementInstructions: XRPSettlementInstructions, obligation: Obligation.State<*>): VerifySettlement.VerifyResult {
        // Get the payment reference.
        val paymentReference = settlementInstructions.paymentReference
                ?: throw IllegalStateException("No transaction hash has been specified yet.")

        val upToDate = clientsForVerification.all { client ->
            val serverState = client.serverState().state.serverState
            serverState in setOf("tracking", "full", "validating", "proposing")
        }

        val isPastLastLedger = clientsForVerification.all { client ->
            settlementInstructions.lastLedgerSequence > client.ledgerIndex().ledgerCurrentIndex
        }

        // The oracle can (and probably should) use more than one node to verify payment occurred.
        // TODO: Deal with errors here e.g. transaction not found.
        val results = clientsForVerification.map { client -> client.transaction(paymentReference) }
        val destinationCorrect = results.all { it.destination == settlementInstructions.accountToPay }
        val amountCorrect = results.all { it.amount == obligation.faceAmount.toXRPAmount() }
        val referenceCorrect = results.all { it.invoiceId == SecureHash.sha256(obligation.linearId.id.toString()).toString() }
        val hasSucceeded = results.all { it.hasSucceeded() }

        val hasSettled = destinationCorrect && amountCorrect && referenceCorrect && hasSucceeded && upToDate

        return when {
            hasSettled && !isPastLastLedger -> VerifySettlement.VerifyResult.SUCCESS
            hasSettled && isPastLastLedger -> VerifySettlement.VerifyResult.SUCCESS
            !hasSettled && isPastLastLedger -> VerifySettlement.VerifyResult.TIMEOUT
            !hasSettled && !isPastLastLedger -> VerifySettlement.VerifyResult.PENDING
            else -> throw IllegalStateException("Shouldn't happen!")
        }
    }
}
