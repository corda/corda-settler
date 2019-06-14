package com.r3.corda.finance.manual.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.finance.obligation.contracts.ObligationContract
import com.r3.corda.finance.obligation.contracts.commands.ObligationCommands
import com.r3.corda.finance.obligation.contracts.states.Obligation
import com.r3.corda.finance.obligation.contracts.types.PaymentReference
import com.r3.corda.finance.obligation.contracts.types.PaymentStatus
import com.r3.corda.finance.obligation.workflows.getLinearStateById
import com.r3.corda.finance.obligation.workflows.resolver
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.ProgressTracker

object UpdatePaymentStatusManually {

    @InitiatingFlow
    @StartableByRPC
    class Initiator(
            private val paymentReference: PaymentReference,
            private val status: PaymentStatus,
            private val linearId: UniqueIdentifier
    ) : FlowLogic<WireTransaction>() {

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
        override fun call(): WireTransaction {
            progressTracker.currentStep = INITIALISING
            val obligationStateAndRef = getLinearStateById<Obligation<*>>(linearId, serviceHub)
                    ?: throw IllegalArgumentException("LinearId not recognised.")
            val obligationState = obligationStateAndRef.state.data

            val os = obligationState.withWellKnownIdentities(resolver)
            val obligee = os.obligee
            check(ourIdentity == obligee) { "This flow can only be started by the obligee. " }
            val obligor = serviceHub.identityService.requireWellKnownPartyFromAnonymous(os.obligor)
            val payment = obligationState.payments.find { it.paymentReference == paymentReference }
                    ?: throw IllegalArgumentException("Could not find payment with reference '$paymentReference'")

            progressTracker.currentStep = BUILDING
            payment.status = status
            val notary = serviceHub.networkMapCache.notaryIdentities.firstOrNull()
                    ?: throw FlowException("No available notary.")
            val utx = TransactionBuilder(notary = notary).apply {
                addInputState(obligationStateAndRef)
                val signers = obligationState.participants.map { it.owningKey }
                addCommand(ObligationCommands.UpdatePayment(payment.paymentReference), signers)
                addOutputState(obligationState, ObligationContract.CONTRACT_REF)
            }

            progressTracker.currentStep = SIGNING
            val signedTx = serviceHub.signInitialTransaction(utx)

            progressTracker.currentStep = COLLECTING
            val obligorFlow = initiateFlow(obligor)
            val stx = subFlow(CollectSignaturesFlow(
                    partiallySignedTx = signedTx,
                    sessionsToCollectFrom = setOf(obligorFlow),
                    myOptionalKeys = listOf(ourIdentity.owningKey),
                    progressTracker = COLLECTING.childProgressTracker())
            )

            // Step 6. Finalise and return the transaction.
            progressTracker.currentStep = FINALISING
            val ntx = subFlow(FinalityFlow(stx, setOf(obligorFlow), FINALISING.childProgressTracker()))
            return ntx.tx
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(val otherFlow: FlowSession) : FlowLogic<WireTransaction>() {
        @Suspendable
        override fun call(): WireTransaction {
            val flow = object : SignTransactionFlow(otherFlow) {
                @Suspendable
                override fun checkTransaction(stx: SignedTransaction) {
                    // TODO: Do some basic checking here.
                    // Reach out to human operator when HCI is available.
                }
            }
            val stx = subFlow(flow)
            return subFlow(ReceiveFinalityFlow(otherFlow, stx.id)).tx
        }
    }
}