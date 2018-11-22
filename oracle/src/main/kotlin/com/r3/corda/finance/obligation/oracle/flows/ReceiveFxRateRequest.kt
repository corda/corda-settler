package com.r3.corda.finance.obligation.oracle.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.finance.obligation.FxRateRequest
import com.r3.corda.finance.obligation.oracle.services.FxRateService
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession


class ReceiveFxRateRequest(val otherSession: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val request = otherSession.receive<FxRateRequest>()
        val fxRateService = serviceHub.cordaService(FxRateService::class.java)
    }
}