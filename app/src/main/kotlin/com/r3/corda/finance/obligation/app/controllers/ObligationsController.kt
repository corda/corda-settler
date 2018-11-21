package com.r3.corda.finance.obligation.app.controllers

import com.r3.corda.finance.obligation.Obligation
import javafx.collections.FXCollections
import net.corda.client.jfx.utils.observeOnFXThread
import net.corda.core.contracts.StateAndRef
import net.corda.core.identity.AbstractParty
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.vaultTrackBy
import tornadofx.*


class ObligationsController : Controller() {

    private val cordaRpcOps: CordaRPCOps by param()
    private val obligationsFeed = cordaRpcOps.vaultTrackBy<Obligation<*>>()

    val obligations = FXCollections.observableList(obligationsFeed.snapshot.states.stateAndRefToResolvedState()).apply {
        obligationsFeed.updates.observeOnFXThread().subscribe {
            removeAll(it.consumed.stateAndRefToResolvedState())
            addAll(it.produced.stateAndRefToResolvedState())
        }
    }

    private fun Collection<StateAndRef<Obligation<*>>>.stateAndRefToResolvedState(): List<Obligation<*>> {
        return map { obligationStateAndRef ->
            val obligation = obligationStateAndRef.state.data
            val resolver = { abstractParty: AbstractParty -> cordaRpcOps.wellKnownPartyFromAnonymous(abstractParty)!! }
            obligation.withWellKnownIdentities(resolver)
        }
    }

}