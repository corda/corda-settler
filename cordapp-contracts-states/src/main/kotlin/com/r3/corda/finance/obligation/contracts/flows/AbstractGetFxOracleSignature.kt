package com.r3.corda.finance.obligation.contracts.flows

import net.corda.core.crypto.TransactionSignature
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow

@InitiatingFlow
abstract class AbstractGetFxOracleSignature : FlowLogic<TransactionSignature>()