package net.corda.finance.obligation.client.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.confidential.SwapIdentitiesFlow
import net.corda.core.contracts.Amount
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.seconds
import net.corda.finance.obligation.contracts.Obligation
import java.security.PublicKey

object CreateObligation {

    @CordaSerializable
    enum class InitiatorRole {
        OBLIGOR,
        OBLIGEE
    }

    @InitiatingFlow
    @StartableByRPC
    class Initiator<T : Any>(
            private val amount: Amount<T>,
            private val role: InitiatorRole,
            private val counterparty: Party,
            private val anonymous: Boolean = true
    ) : FlowLogic<SignedTransaction>() {

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
        private fun createAnonymousObligation(): Pair<Obligation.State<T>, PublicKey> {
            val txKeys = subFlow(SwapIdentitiesFlow(counterparty))
            // SwapIdentityFlow should return two keys.
            check(txKeys.size == 2) { "Something went wrong when generating confidential identities." }
            val anonymousMe = txKeys[ourIdentity] ?: throw FlowException("Couldn't create our conf. identity.")
            val anonymousObligor = txKeys[counterparty]
                    ?: throw FlowException("Couldn't create lender's conf. identity.")
            return createObligation(us = anonymousMe, them = anonymousObligor)
        }

        private fun createObligation(us: AbstractParty, them: AbstractParty): Pair<Obligation.State<T>, PublicKey> {
            check(us != them) { "You cannot create an obligation to yourself" }
            val obligation = when (role) {
                InitiatorRole.OBLIGEE -> Obligation.State(amount, them, us)
                InitiatorRole.OBLIGOR -> Obligation.State(amount, us, them)
            }
            return Pair(obligation, us.owningKey)
        }

        @Suspendable
        override fun call(): SignedTransaction {
            // Step 1. Initialisation.
            progressTracker.currentStep = INITIALISING
            val (obligation, signingKey) = if (anonymous) {
                createAnonymousObligation()
            } else {
                createObligation(us = ourIdentity, them = counterparty)
            }

            // Step 2. Building.
            progressTracker.currentStep = BUILDING
            val notary = serviceHub.networkMapCache.notaryIdentities.firstOrNull()
                    ?: throw FlowException("No available notary.")
            val utx = TransactionBuilder(notary = notary).apply {
                addOutputState(obligation, Obligation.CONTRACT_REF)
                val signers = obligation.participants.map { it.owningKey }
                addCommand(Obligation.Commands.Create(), signers)
                setTimeWindow(serviceHub.clock.instant(), 30.seconds)
            }

            // Step 3. Sign the transaction.
            progressTracker.currentStep = SIGNING
            val ptx = serviceHub.signInitialTransaction(utx, signingKey)

            // Step 4. Get the counterparty signature.
            progressTracker.currentStep = COLLECTING
            val lenderFlow = initiateFlow(counterparty)
            val stx = subFlow(CollectSignaturesFlow(
                    partiallySignedTx = ptx,
                    sessionsToCollectFrom = setOf(lenderFlow),
                    myOptionalKeys = listOf(signingKey),
                    progressTracker = COLLECTING.childProgressTracker())
            )

            // Step 5. Finalise and return the transaction.
            progressTracker.currentStep = FINALISING
            return subFlow(FinalityFlow(stx, FINALISING.childProgressTracker()))
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(val otherFlow: FlowSession) : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {
            val flow = object : SignTransactionFlow(otherFlow) {
                @Suspendable
                override fun checkTransaction(stx: SignedTransaction) = Unit // TODO: Do some checking here.
            }
            val stx = subFlow(flow)
            // Suspend this flow until the transaction is committed.
            return waitForLedgerCommit(stx.id)
        }
    }
}