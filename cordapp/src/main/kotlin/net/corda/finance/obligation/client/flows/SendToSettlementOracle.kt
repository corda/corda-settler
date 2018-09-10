package net.corda.finance.obligation.client.flows

import net.corda.core.contracts.StateAndRef
import net.corda.finance.obligation.contracts.Obligation
import net.corda.finance.obligation.flows.SendToSettlementOracle

class SendToSettlementOracle(obligationStateAndRef: StateAndRef<Obligation.State<*>>) : SendToSettlementOracle(obligationStateAndRef)