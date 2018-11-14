package com.r3.corda.finance.obligation.flows

import net.corda.core.flows.FlowLogic
import net.corda.core.transactions.SignedTransaction

abstract class AbstractMakeOffLedgerPayment : FlowLogic<SignedTransaction>()