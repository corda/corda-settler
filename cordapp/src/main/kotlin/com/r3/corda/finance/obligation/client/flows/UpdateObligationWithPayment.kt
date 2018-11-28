package com.r3.corda.finance.obligation.client.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.finance.obligation.types.Money
import com.r3.corda.finance.obligation.types.Payment
import com.r3.corda.finance.obligation.client.getLinearStateById
import com.r3.corda.finance.obligation.client.resolver
import com.r3.corda.finance.obligation.commands.ObligationCommands
import com.r3.corda.finance.obligation.contracts.ObligationContract
import com.r3.corda.finance.obligation.states.Obligation
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

@StartableByRPC
class UpdateObligationWithPayment<T : Money>(val linearId: UniqueIdentifier, val paymentInformation: Payment<T>) : FlowLogic<SignedTransaction>() {

    override val progressTracker: ProgressTracker = ProgressTracker()

    @Suspendable
    override fun call(): SignedTransaction {
        val obligationStateAndRef = getLinearStateById<Obligation<T>>(linearId, serviceHub)
                ?: throw IllegalArgumentException("LinearId not recognised.")
        val obligation = obligationStateAndRef.state.data

        // 2. This flow should only be started by the beneficiary.
        val obligor = obligation.withWellKnownIdentities(resolver).obligor
        check(ourIdentity == obligor) { "This flow can only be started by the obligor. " }

        // 3. Add payment to obligation.
        val obligationWithNewPayment = obligation.withPayment(paymentInformation)

        // 4. Creating a sign new transaction.
        val signingKey = listOf(obligation.obligor.owningKey)
        val notary = serviceHub.networkMapCache.notaryIdentities.firstOrNull()
                ?: throw FlowException("No available notary.")
        val utx = TransactionBuilder(notary = notary).apply {
            addInputState(obligationStateAndRef)
            addOutputState(obligationWithNewPayment, ObligationContract.CONTRACT_REF)
            addCommand(ObligationCommands.AddPayment(), signingKey)
        }

        // 5. Sign transaction.
        val stx = serviceHub.signInitialTransaction(utx, signingKey)

        // 6. Finalise transaction and send to participants.
        return subFlow(FinalityFlow(stx))
    }
}