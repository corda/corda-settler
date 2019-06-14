package com.r3.corda.finance.obligation.contracts.flows

import net.corda.core.flows.FlowLogic
import net.corda.core.transactions.SignedTransaction

abstract class AbstractMakeOffLedgerPayment : FlowLogic<SignedTransaction>()