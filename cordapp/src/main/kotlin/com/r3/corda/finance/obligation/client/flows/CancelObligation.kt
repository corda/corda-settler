package com.r3.corda.finance.obligation.client.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.finance.obligation.client.getLinearStateById
import com.r3.corda.finance.obligation.client.resolver
import com.r3.corda.finance.obligation.commands.ObligationCommands
import com.r3.corda.finance.obligation.states.Obligation
import com.r3.corda.finance.obligation.types.Money
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

object CancelObligation {

    @InitiatingFlow
    @StartableByRPC
    class Initiator(val linearId: UniqueIdentifier) : FlowLogic<SignedTransaction>() {

        companion object {
            object INITIALISING : ProgressTracker.Step("Performing initial steps.")
            object BUILDING : ProgressTracker.Step("Building and verifying transaction.")
            object SIGNING : ProgressTracker.Step("signing transaction.")

            object COLLECTING : ProgressTracker.Step("Collecting counterparty signature.") {
                override fun childProgressTracker() = CollectSignaturesFlow.tracker()
            }

            object FINALISING : ProgressTracker.Step("Finalising transaction.") {
                override fun childProgressTracker() = FinalityFlow.tracker()
            }

            fun tracker() = ProgressTracker(INITIALISING, BUILDING, SIGNING, COLLECTING, FINALISING)
        }

        override val progressTracker: ProgressTracker = tracker()

        @Suspendable
        override fun call(): SignedTransaction {
            // Get the obligation from our vault.
            progressTracker.currentStep = INITIALISING
            val obligationStateAndRef = getLinearStateById<Obligation<Money>>(linearId, serviceHub)
                    ?: throw IllegalArgumentException("LinearId not recognised.")
            val obligation = obligationStateAndRef.state.data
            val obligationWithWellKnownParties = obligation.withWellKnownIdentities(resolver)
            // Generate output and required signers list based based upon supplied command.

            // Create the new transaction.
            progressTracker.currentStep = BUILDING
            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val signers = obligation.participants.map { it.owningKey }
            val utx = TransactionBuilder(notary = notary).apply {
                addInputState(obligationStateAndRef)
                addCommand(ObligationCommands.Cancel(), signers)
            }

            // Get the counterparty and our signing key.
            val (us, counterparty) = if (obligationWithWellKnownParties.obligor == ourIdentity) {
                Pair(obligation.obligor, obligationWithWellKnownParties.obligee)
            } else {
                Pair(obligation.obligee, obligationWithWellKnownParties.obligor)
            }

            // Sign it.
            progressTracker.currentStep = SIGNING
            val ptx = serviceHub.signInitialTransaction(utx, us.owningKey)

            // Get the counterparty's signature.
            progressTracker.currentStep = COLLECTING
            val couterpartyFlow = initiateFlow(counterparty as Party)
            val stx = subFlow(CollectSignaturesFlow(
                    partiallySignedTx = ptx,
                    sessionsToCollectFrom = setOf(couterpartyFlow),
                    myOptionalKeys = listOf(us.owningKey),
                    progressTracker = COLLECTING.childProgressTracker()
            ))

            progressTracker.currentStep = FINALISING
            return subFlow(FinalityFlow(stx, FINALISING.childProgressTracker()))
        }

    }

    @InitiatedBy(Initiator::class)
    class Respnder(val otherSession: FlowSession) : FlowLogic<SignedTransaction>() {

        @Suspendable
        override fun call(): SignedTransaction {
            val flow = object : SignTransactionFlow(otherSession) {
                @Suspendable
                override fun checkTransaction(stx: SignedTransaction) {
                    // TODO: Do some basic checking here.
                    // Reach out to human operator when HCI is available.
                }
            }
            val stx = subFlow(flow)
            // Suspend this flow until the transaction is committed.
            return waitForLedgerCommit(stx.id)
        }

    }

}