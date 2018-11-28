//package com.r3.corda.finance.obligation.client.flows
//
//import co.paralleluniverse.fibers.Suspendable
//import com.r3.corda.finance.obligation.types.Money
//import net.corda.core.contracts.Amount
//import net.corda.core.contracts.UniqueIdentifier
//import net.corda.core.flows.*
//import net.corda.core.identity.Party
//import net.corda.core.transactions.SignedTransaction
//import java.time.Instant
//
//object CancelObligation {
//
//    @InitiatingFlow
//    @StartableByRPC
//    class Initiator(val lienarId: UniqueIdentifier) : FlowLogic<SignedTransaction>() {
//
//        @Suspendable
//        override fun call(): SignedTransaction {
//
//        }
//
//    }
//
//    @InitiatedBy(Initiator::class)
//    class Respnder(val otherSession: FlowSession) : FlowLogic<SignedTransaction>() {
//
//        @Suspendable
//        override fun call(): SignedTransaction {
//
//        }
//
//    }
//
//}