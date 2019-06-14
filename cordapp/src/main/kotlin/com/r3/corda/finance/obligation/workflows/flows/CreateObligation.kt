package com.r3.corda.finance.obligation.workflows.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.finance.obligation.contracts.ObligationContract
import com.r3.corda.finance.obligation.contracts.commands.ObligationCommands
import com.r3.corda.finance.obligation.contracts.states.Obligation
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.confidential.SwapIdentitiesFlow
import net.corda.core.contracts.Amount
import net.corda.core.flows.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.transactions.WireTransaction
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.seconds
import net.corda.core.utilities.unwrap
import java.security.PublicKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

object CreateObligation {

    @CordaSerializable
    enum class InitiatorRole {
        OBLIGOR,
        OBLIGEE
    }

    @InitiatingFlow
    @StartableByRPC
    class Initiator<T : TokenType>(
            private val amount: Amount<T>,
            private val role: InitiatorRole,
            private val counterparty: Party,
            private val dueBy: Instant? = null,
            private val anonymous: Boolean = true
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
        private fun createAnonymousObligation(lenderFlow: FlowSession): Pair<Obligation<T>, PublicKey> {
            // TODO: Update to use the new confidential identities constructor.
            val txKeys = subFlow(SwapIdentitiesFlow(lenderFlow))
            // SwapIdentityFlow should return two keys.
            check(txKeys.size == 2) { "Something went wrong when generating confidential identities." }
            val anonymousMe = txKeys[ourIdentity] ?: throw FlowException("Couldn't create our conf. identity.")
            val anonymousObligor = txKeys[counterparty]
                    ?: throw FlowException("Couldn't create lender's conf. identity.")
            return createObligation(us = anonymousMe, them = anonymousObligor)
        }

        private fun createObligation(us: AbstractParty, them: AbstractParty): Pair<Obligation<T>, PublicKey> {
            check(us != them) { "You cannot create an obligation to yourself" }
            val obligation = when (role) {
                InitiatorRole.OBLIGEE -> Obligation(amount, them, us, dueBy)
                InitiatorRole.OBLIGOR -> Obligation(amount, us, them, dueBy)
            }
            return Pair(obligation, us.owningKey)
        }

        @Suspendable
        override fun call(): WireTransaction {
            // Step 1. Initialisation.
            progressTracker.currentStep = INITIALISING
            val lenderFlow = initiateFlow(counterparty)
            val (obligation, signingKey) = if (anonymous) {
                lenderFlow.send("anonymous")
                createAnonymousObligation(lenderFlow)
            } else {
                lenderFlow.send("normal")
                createObligation(us = ourIdentity, them = counterparty)
            }

            // Step 2. Check parameters.
            if (dueBy != null) {
                val todayUTC = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC)
                require(dueBy > todayUTC) {
                    "Due by date must be in the future."
                }
            }

            // Step 3. Building.
            progressTracker.currentStep = BUILDING
            val notary = serviceHub.networkMapCache.notaryIdentities.firstOrNull()
                    ?: throw FlowException("No available notary.")
            val utx = TransactionBuilder(notary = notary).apply {
                addOutputState(obligation, ObligationContract.CONTRACT_REF)
                val signers = obligation.participants.map { it.owningKey }
                addCommand(ObligationCommands.Create(), signers)
                setTimeWindow(serviceHub.clock.instant(), 30.seconds)
            }

            // Step 4. Sign the transaction.
            progressTracker.currentStep = SIGNING
            val ptx = serviceHub.signInitialTransaction(utx, signingKey)

            // Step 5. Get the counterparty signature.
            progressTracker.currentStep = COLLECTING
            val stx = subFlow(CollectSignaturesFlow(
                    partiallySignedTx = ptx,
                    sessionsToCollectFrom = setOf(lenderFlow),
                    myOptionalKeys = listOf(signingKey),
                    progressTracker = COLLECTING.childProgressTracker())
            )

            // Step 6. Finalise and return the transaction.
            progressTracker.currentStep = FINALISING
            val ntx = subFlow(FinalityFlow(stx, setOf(lenderFlow), FINALISING.childProgressTracker()))
            return ntx.tx
        }
    }

    @InitiatedBy(Initiator::class)
    class Responder(val otherFlow: FlowSession) : FlowLogic<WireTransaction>() {
        @Suspendable
        override fun call(): WireTransaction {
            val type = otherFlow.receive<String>().unwrap { it }
            if (type == "anonymous") subFlow(SwapIdentitiesFlow(otherFlow))
            val flow = object : SignTransactionFlow(otherFlow) {
                @Suspendable
                override fun checkTransaction(stx: SignedTransaction) {
                    // TODO: Do some basic checking here.
                    // Reach out to human operator when HCI is available.
                }
            }
            val stx = subFlow(flow)
            // Suspend this flow until the transaction is committed.
            return subFlow(ReceiveFinalityFlow(otherFlow, stx.id)).tx
        }
    }
}
