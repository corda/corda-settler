package com.r3.corda.finance.obligation.workflows

import net.corda.core.contracts.LinearState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.AbstractParty
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria

/** Gets a linear state by unique identifier. */
inline fun <reified T : LinearState> getLinearStateById(linearId: UniqueIdentifier, services: ServiceHub): StateAndRef<T>? {
    val query = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))
    return services.vaultService.queryBy<T>(query).states.singleOrNull()
}

/** Lambda for resolving an [AbstractParty] to a [Party]. */
val FlowLogic<*>.resolver
    get() = { abstractParty: AbstractParty ->
        serviceHub.identityService.requireWellKnownPartyFromAnonymous(abstractParty)
    }
