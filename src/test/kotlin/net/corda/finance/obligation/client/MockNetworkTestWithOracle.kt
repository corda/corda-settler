package net.corda.finance.obligation.client

import net.corda.core.identity.CordaX500Name
import net.corda.testing.node.internal.InternalMockNodeParameters
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.cordappsForPackages

abstract class MockNetworkTestWithOracle(numberOfNodes: Int) : MockNetworkTest(numberOfNodes = numberOfNodes) {
    var Oracle: TestStartedNode = network.createNode(
            InternalMockNodeParameters(
                    legalName = CordaX500Name("Oracle", "London", "GB"),
                    additionalCordapps = cordappsForPackages("net.corda.finance.obligation.oracle")
            )
    )
}