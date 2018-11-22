package com.r3.corda.finance.obligation.client.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.finance.obligation.FxRateRequest
import com.r3.corda.finance.obligation.Money
import com.r3.corda.finance.obligation.client.getLinearStateById
import com.r3.corda.finance.obligation.commands.ObligationCommands
import com.r3.corda.finance.obligation.contracts.ObligationContract
import com.r3.corda.finance.obligation.states.Obligation
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import java.security.PublicKey

// If the payment currency for the settlement method doesn't match the new face amount token then it is removed.

object NovateObligation {

    @InitiatingFlow
    @StartableByRPC
    class Initiator(
            val linearId: UniqueIdentifier,
            val novationCommand: ObligationCommands.Novate
    ) : FlowLogic<SignedTransaction>() {

        @Suspendable
        fun handleUpdateFaceAmountToken(obligation: Obligation<Money>): Pair<Obligation<Money>, List<PublicKey>> {
            // Cast to correct type first.
            novationCommand as ObligationCommands.Novate.UpdateFaceAmountToken<*, *>
            val fxRateRequest = FxRateRequest(
                    baseCurrency = novationCommand.oldToken,
                    foreignCurrency = novationCommand.newToken
            )
            val fxRate = subFlow(GetFxRate(fxRateRequest, novationCommand.oracle))

            // Get new amount using the fx rate.
            // Update the obligation with the new amount and token type.

        }

        private fun handleNovationCommand(obligation: Obligation<Money>): Pair<Obligation<Money>, List<PublicKey>> {
            val defaultSigners = obligation.participants.map { it.owningKey }
            return when (novationCommand) {
                is ObligationCommands.Novate.UpdateDueBy -> {
                    val obligationWithUpdatedDueDate = obligation.withDueByDate(novationCommand.newDueBy)
                    Pair(obligationWithUpdatedDueDate, defaultSigners)
                }
                is ObligationCommands.Novate.UpdateParty -> {
                    val obligationWithNewParty = obligation.withNewCounterparty(
                            oldParty = novationCommand.oldParty,
                            newParty = novationCommand.newParty
                    )
                    Pair(obligationWithNewParty, defaultSigners)
                }
                is ObligationCommands.Novate.UpdateFaceAmountQuantity -> {
                    val obligationWithNewFaceAmountQuantity = obligation.withNewFaceValueQuantity(novationCommand.newAmount)
                    Pair(obligationWithNewFaceAmountQuantity, defaultSigners)
                }
                is ObligationCommands.Novate.UpdateFaceAmountToken<*, *> -> handleUpdateFaceAmountToken(obligation)
            }
        }

        @Suspendable
        override fun call(): SignedTransaction {
            // Get the obligation from our vault.
            val obligationStateAndRef = getLinearStateById<Obligation<Money>>(linearId, serviceHub)
                    ?: throw IllegalArgumentException("LinearId not recognised.")
            val notary = serviceHub.networkMapCache.notaryIdentities.first()
            val obligation = obligationStateAndRef.state.data

            // Generate output and required signers list based based upon supplied command.
            val (output, signers) = handleNovationCommand(obligation)

            // Build the transaction.
            val utx = TransactionBuilder(notary = notary).apply {
                addInputState(obligationStateAndRef)
                addOutputState(output, ObligationContract.CONTRACT_REF)
                addCommand(novationCommand, signers)
            }

            // Sign the transaction.
            val stx = serviceHub.signInitialTransaction(utx, signers)

        }
    }

    @InitiatedBy(Initiator::class)
    class Responder : FlowLogic<SignedTransaction>() {
        @Suspendable
        override fun call(): SignedTransaction {

        }
    }

}
