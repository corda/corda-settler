package net.corda.finance.obligation.flows

import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatingFlow
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction

@InitiatingFlow
abstract class AbstractSendToSettlementOracle : FlowLogic<SignedTransaction>()

@CordaSerializable
sealed class OracleResult {
    data class Success(val stx: SignedTransaction) : OracleResult()
    data class Failure(val message: String) : OracleResult()
}