//package net.corda.finance.obligation.flows
//
//import co.paralleluniverse.fibers.Suspendable
//import net.corda.core.contracts.StateAndRef
//import net.corda.core.flows.FlowLogic
//import net.corda.core.flows.InitiatingFlow
//import net.corda.core.transactions.SignedTransaction
//import net.corda.finance.obligation.contracts.Obligation
//
//@InitiatingFlow
//abstract class SendToSettlementOracle(
//        protected val obligationStateAndRef: StateAndRef<Obligation.State<*>>
//) : FlowLogic<SignedTransaction>() {
//
//    @Suspendable
//    override fun call(): SignedTransaction {
//        val obligation = obligationStateAndRef.state.data
//        val settlementInstructions = obligation.settlementInstructions
//    }
//
//}