package net.corda.finance.obligation.oracle.services

import com.typesafe.config.ConfigFactory
import net.corda.core.crypto.SecureHash
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.finance.obligation.contracts.Obligation
import net.corda.finance.ripple.RippleClientForVerification
import net.corda.finance.ripple.types.RippleSettlementInstructions
import net.corda.finance.ripple.utilities.toRippleAmount
import java.net.URI

const val configFileName = "ripple.conf"

@CordaService
class RippleOracleService(val services: AppServiceHub) : SingletonSerializeAsToken() {
    private val nodes by lazy { ConfigFactory.parseResources(configFileName).getStringList("nodes").mapNotNull(::URI) }
    private val clientsForVersification = nodes.map { nodeUri -> RippleClientForVerification(nodeUri) }

    fun hasPaymentSettled(settlementInstructions: RippleSettlementInstructions, obligation: Obligation.State<*>): Boolean {
        val paymentReference = settlementInstructions.paymentReference
                ?: throw IllegalStateException("No transaction hash has been specified yet.")
        val results = clientsForVersification.map { client -> client.transaction(paymentReference) }
        println(results)
        val destinationCorrect = results.all { it.destination == settlementInstructions.accountToPay }
        val amountCorrect = results.all { it.amount == obligation.amount.toRippleAmount() } // Less fees?
        val referenceCorrect = results.all { it.invoiceId == SecureHash.sha256(obligation.linearId.id.toString()).toString() }
        return destinationCorrect && amountCorrect && referenceCorrect
    }
}
