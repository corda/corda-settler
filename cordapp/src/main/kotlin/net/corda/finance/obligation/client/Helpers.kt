package net.corda.finance.obligation.client

import net.corda.core.contracts.LinearState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.node.ServiceHub
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import com.ripple.core.coretypes.Amount as RippleAmount

inline fun <reified T : LinearState> getLinearStateById(
        linearId: UniqueIdentifier,
        services: ServiceHub
): StateAndRef<T>? {
    val query = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))
    return services.vaultService.queryBy<T>(query).states.singleOrNull()
}