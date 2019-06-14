package com.r3.corda.finance.obligation.oracle.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.finance.obligation.contracts.flows.AbstractGetFxOracleSignature
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignableData
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.transactions.FilteredTransaction
import net.corda.core.utilities.unwrap

/** Assumption here is that the Fx rate is always correct. */
@InitiatedBy(AbstractGetFxOracleSignature::class)
class ReceiveSigningRequest(val otherSession: FlowSession) : FlowLogic<Unit>() {
    @Suspendable
    override fun call() {
        val request = otherSession.receive<FilteredTransaction>().unwrap { it }
        // As this is just a stub implementation, we can sign anything which is sent to us.
        // In a full implementation, the Oracle would check that the exchange rate is correct to some tolerance.
        val signatureMetadata = SignatureMetadata(serviceHub.myInfo.platformVersion, Crypto.findSignatureScheme(ourIdentity.owningKey).schemeNumberID)
        val signableData = SignableData(request.id, signatureMetadata)
        val signature = serviceHub.keyManagementService.sign(signableData, ourIdentity.owningKey)
        otherSession.send(signature)
    }
}