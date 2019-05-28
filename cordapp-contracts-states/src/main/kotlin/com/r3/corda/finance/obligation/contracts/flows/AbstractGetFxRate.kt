package com.r3.corda.finance.obligation.contracts.flows

import com.r3.corda.finance.obligation.contracts.types.FxRateResponse
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow

@InitiatingFlow
abstract class AbstractGetFxRate : FlowLogic<FxRateResponse>()