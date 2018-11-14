package net.corda.finance.obligation.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.AbstractParty
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.finance.obligation.contracts.Obligation
import net.corda.finance.obligation.getLinearStateById
import net.corda.finance.obligation.types.OffLedgerSettlementInstructions

@StartableByRPC
class UpdateObligationWithPaymentRef(val linearId: UniqueIdentifier, val paymentReference: String) : FlowLogic<SignedTransaction>() {

    @Suspendable
    override fun call(): SignedTransaction {
        val obligationStateAndRef = getLinearStateById<Obligation.State<Any>>(linearId, serviceHub)
                ?: throw IllegalArgumentException("LinearId not recognised.")
        val obligation = obligationStateAndRef.state.data

        // 2. This flow should only be started by the beneficiary.
        val identityResolver = { abstractParty: AbstractParty ->
            serviceHub.identityService.requireWellKnownPartyFromAnonymous(abstractParty)
        }
        val obligor = obligation.withWellKnownIdentities(identityResolver).obligor
        check(ourIdentity == obligor) { "This flow can only be started by the obligor. " }

        val settlementInstructions = obligation.settlementInstructions as OffLedgerSettlementInstructions<*>

        // 5. Add payment reference to settlement instructions and update state.
        val updatedSettlementInstructions = settlementInstructions.addPaymentReference(paymentReference)
        val obligationWithUpdatedSettlementInstructions = obligation.withSettlementTerms(updatedSettlementInstructions)

        // 6. Add updated settlement terms to obligation.
        val signingKey = listOf(obligation.obligor.owningKey)
        val notary = serviceHub.networkMapCache.notaryIdentities.firstOrNull()
                ?: throw FlowException("No available notary.")
        val utx = TransactionBuilder(notary = notary).apply {
            addInputState(obligationStateAndRef)
            addOutputState(obligationWithUpdatedSettlementInstructions, Obligation.CONTRACT_REF)
            addCommand(Obligation.Commands.AddPaymentDetails(), signingKey)
        }

        // 7. Sign transaction.
        val stx = serviceHub.signInitialTransaction(utx, signingKey)

        // 8. Finalise transaction and send to participants.
        return subFlow(FinalityFlow(stx))
    }
}