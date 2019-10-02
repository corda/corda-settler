package com.r3.corda.lib.settler

import com.r3.corda.lib.obligation.contracts.commands.ObligationCommands
import com.r3.corda.lib.obligation.contracts.states.Obligation
import com.r3.corda.lib.obligation.contracts.types.SettlementMethod
import com.r3.corda.lib.obligation.workflows.CancelObligation
import com.r3.corda.lib.obligation.workflows.CreateObligation
import com.r3.corda.lib.obligation.workflows.InitiatorRole
import com.r3.corda.lib.obligation.workflows.NovateObligation
import com.r3.corda.lib.settler.workflows.flows.OffLedgerSettleObligation
import com.r3.corda.lib.settler.workflows.flows.UpdateSettlementMethod
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.Amount
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.toFuture
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.WireTransaction
import net.corda.testing.node.MockNetwork
import net.corda.testing.node.StartedMockNode
import org.junit.After
import org.junit.Before
import java.time.Instant

abstract class MockNetworkTest(val numberOfNodes: Int) {

    protected val network = MockNetwork(
            cordappPackages = listOf(
                    "com.r3.corda.lib.tokens.money",
                    "com.r3.corda.lib.tokens.contracts",
                    "com.r3.corda.lib.ci.workflows",
                    "com.r3.corda.lib.obligation.contracts",
                    "com.r3.corda.lib.obligation.oracle.flows",
                    "com.r3.corda.lib.obligation.api",
                    "com.r3.corda.lib.obligation.workflows",
                    "com.r3.corda.lib.settler.swift",
                    "com.r3.corda.lib.settler.oracle",
                    "com.r3.corda.lib.settler.ripple",
                    "com.r3.corda.lib.settler.manual",
                    "com.r3.corda.lib.settler.api",
                    "com.r3.corda.lib.settler.workflows"
            ),
            threadPerNode = true
    )

    /** The nodes which makes up the network. */
    protected lateinit var nodes: List<StartedMockNode>

    /** Override this to assign each node to a variable for ease of use. */
    @Before
    abstract fun initialiseNodes()

    @Before
    fun setupNetwork() {
        nodes = createSomeNodes(numberOfNodes)
    }

    @After
    fun tearDownNetwork() {
        network.stopNodes()
    }

    private fun createSomeNodes(numberOfNodes: Int = 2): List<StartedMockNode> {
        val partyNodes = (1..numberOfNodes).map { current ->
            val char = current.toChar() + 64
            val name = CordaX500Name("Party$char", "London", "GB")
            network.createPartyNode(name)
        }
        return partyNodes
    }

    /** Create a new obligation with the supplied parameters. */
    fun StartedMockNode.createObligation(
            faceAmount: Amount<TokenType>,
            counterparty: StartedMockNode,
            role: InitiatorRole,
            dueBy: Instant = Instant.now().plusSeconds(10000)
    ): CordaFuture<WireTransaction> {
        return transaction {
            val flow = CreateObligation(faceAmount, role, counterparty.legalIdentity(), dueBy)
            startFlow(flow)
        }
    }

    /** Cancel an obligation. */
    fun StartedMockNode.cancelObligation(linearId: UniqueIdentifier): CordaFuture<SignedTransaction> {
        return transaction {
            val flow = CancelObligation(linearId)
            startFlow(flow)
        }
    }

    /** Novate an obligation. */
    fun StartedMockNode.novateObligation(
            linearId: UniqueIdentifier,
            novationCommand: ObligationCommands.Novate
    ): CordaFuture<WireTransaction> {
        return transaction {
            val flow = NovateObligation(linearId, novationCommand)
            startFlow(flow)
        }
    }

    /** Add settlement instructions to existing obligation. */
    fun StartedMockNode.addSettlementInstructions(linearId: UniqueIdentifier, settlementMethod: SettlementMethod): CordaFuture<WireTransaction> {
        return transaction {
            val flow = UpdateSettlementMethod.Initiator(linearId, settlementMethod)
            startFlow(flow)
        }
    }

    /** Add settlement instructions to existing obligation. */
    fun <T : TokenType>StartedMockNode.makePayment(amount: Amount<T>, linearId: UniqueIdentifier): CordaFuture<WireTransaction> {
        return transaction { startFlow(OffLedgerSettleObligation.Initiator(amount, linearId)) }
    }

    fun StartedMockNode.legalIdentity() = services.myInfo.legalIdentities.first()

    /** From a transaction which produces a single output, retrieve that output. */
    inline fun <reified T : LinearState> WireTransaction.singleOutput() = outRefsOfType<T>().single()

    inline fun <reified T : LinearState> StateAndRef<T>.linearId() = state.data.linearId

    fun StartedMockNode.queryObligationById(linearId: UniqueIdentifier): StateAndRef<Obligation<TokenType>> {
        return transaction {
            val query = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))
            services.vaultService.queryBy<Obligation<TokenType>>(query).states.single()
        }
    }

    /** Check to see if a node recorded a transaction with a particular hash. Return a future signed transaction. */
    fun StartedMockNode.watchForTransaction(txId: SecureHash): CordaFuture<SignedTransaction> {
        return transaction { services.validatedTransactions.updates.filter { it.id == txId }.toFuture() }
    }

}