package com.r3.corda.finance.obligation.test

import com.r3.corda.finance.obligation.workflows.flows.CreateObligation
import com.r3.corda.sdk.token.money.GBP
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.getOrThrow
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.TestCordapp
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class IntegrationTest {

    companion object {
        private val log = contextLogger()
    }

    private val nodes = listOf(
            NodeParameters(providedName = CordaX500Name("PartyA", "London", "GB")),
            NodeParameters(providedName = CordaX500Name("PartyB", "London", "GB")),
            NodeParameters(
                    providedName = CordaX500Name("Oracle", "London", "GB"),
                    additionalCordapps = listOf(TestCordapp.findCordapp("com.r3.corda.finance.obligation.oracle"))
            )
    )

    private val defaultCorDapps = listOf(
            TestCordapp.findCordapp("com.r3.corda.finance.obligation.contracts"),
            TestCordapp.findCordapp("com.r3.corda.finance.obligation.workflows"),
            TestCordapp.findCordapp("com.r3.corda.finance.ripple"),
            TestCordapp.findCordapp("com.r3.corda.finance.swift")
    )

    private val driverParameters = DriverParameters(
            startNodesInProcess = false,
            cordappsForAllNodes = defaultCorDapps
    )

    @Test
    fun `node test`() {
        driver(driverParameters) {
            val (partyA, partyB, oracle) = nodes.map { params -> startNode(params).getOrThrow() }
            log.info("All nodes started up.")
            partyA.rpc.startFlow(CreateObligation::Initiator,
                    100.GBP,
                    CreateObligation.InitiatorRole.OBLIGOR,
                    partyB.nodeInfo.legalIdentities.first(),
                    Instant.now().plus(1, ChronoUnit.DAYS),
                    true
            ).returnValue.getOrThrow()
        }

    }
}