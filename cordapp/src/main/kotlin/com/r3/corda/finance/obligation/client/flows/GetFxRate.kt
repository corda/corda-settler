package com.r3.corda.finance.obligation.client.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.finance.obligation.types.FxRateRequest
import com.r3.corda.finance.obligation.types.FxRateResponse
import com.r3.corda.finance.obligation.flows.AbstractGetFxRate
import net.corda.core.identity.Party
import net.corda.core.utilities.unwrap

class GetFxRate(private val request: FxRateRequest, private val oracle: Party) : AbstractGetFxRate() {
    @Suspendable
    override fun call(): FxRateResponse {
        val session = initiateFlow(oracle)
        return session.sendAndReceive<FxRateResponse>(request).unwrap { it }
    }
}