package com.r3.corda.finance.obligation.app.controllers

import com.r3.corda.finance.obligation.app.models.UiObligation
import com.r3.corda.finance.obligation.states.Obligation
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import net.corda.client.jfx.utils.observeOnFXThread
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.vaultTrackBy
import tornadofx.*


class ObligationsController : Controller() {

    private val cordaRpcOps: CordaRPCOps by param()
    private val obligationsFeed = cordaRpcOps.vaultTrackBy<Obligation<*>>()

    val obligations: ObservableList<UiObligation> = FXCollections.observableList(obligationsFeed.snapshot.states.stateAndRefToResolvedState()).apply {
        obligationsFeed.updates.observeOnFXThread().subscribe {
            removeAll(it.consumed.stateAndRefToResolvedState())
            addAll(it.produced.stateAndRefToResolvedState())
        }
    }

    private fun Collection<StateAndRef<Obligation<*>>>.stateAndRefToResolvedState(): List<UiObligation> {
        return map { obligationStateAndRef ->
            val resolver = { abstractParty: AbstractParty -> cordaRpcOps.wellKnownPartyFromAnonymous(abstractParty)!! }
            val obligation = obligationStateAndRef.state.data.withWellKnownIdentities(resolver)
            UiObligation(
                    obligation.linearId,
                    obligation.faceAmount,
                    obligation.obligee as Party,
                    obligation.obligor as Party,
                    obligation.settlementStatus,
                    obligation.createdAt,
                    obligation.dueBy,
                    obligation.settlementMethod,
                    obligation.settlementMethod != null,
                    obligation.payments.isNotEmpty(),
                    obligation.payments
            )
        }
    }

}