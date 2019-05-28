package com.r3.corda.finance.obligation.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.finance.obligation.contracts.flows.AbstractGetFxRate
import com.r3.corda.finance.obligation.contracts.types.FxRateRequest
import com.r3.corda.finance.obligation.contracts.types.FxRateResponse
import net.corda.core.identity.Party
import net.corda.core.utilities.unwrap

class GetFxRate(private val request: FxRateRequest, private val oracle: Party) : AbstractGetFxRate() {
    @Suspendable
    override fun call(): FxRateResponse {
        val session = initiateFlow(oracle)
        return session.sendAndReceive<FxRateResponse>(request).unwrap { it }
    }
}