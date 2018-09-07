package net.corda.finance.obligation.client.flows

import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.transactions.SignedTransaction

@InitiatingFlow
abstract class AbstractSettleObligation : FlowLogic<SignedTransaction>()