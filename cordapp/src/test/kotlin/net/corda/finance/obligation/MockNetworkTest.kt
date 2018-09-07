package net.corda.finance.obligation

import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.toFuture
import net.corda.core.transactions.SignedTransaction
import net.corda.finance.obligation.contracts.Obligation
import net.corda.finance.obligation.flows.AddSettlementInstructions
import net.corda.finance.obligation.types.DigitalCurrency
import net.corda.finance.obligation.types.SettlementInstructions
import net.corda.testing.node.internal.InternalMockNetwork
import net.corda.testing.node.internal.TestStartedNode
import net.corda.testing.node.internal.cordappsForPackages
import net.corda.testing.node.internal.startFlow
import org.junit.After
import org.junit.Before

abstract class MockNetworkTest(val numberOfNodes: Int) {

    protected val network = InternalMockNetwork(
            cordappsForAllNodes = cordappsForPackages("net.corda.finance.obligation"),
            threadPerNode = true
    )

    /** The nodes which makes up the network. */
    protected lateinit var nodes: List<TestStartedNode>

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

    private fun createSomeNodes(numberOfNodes: Int = 2): List<TestStartedNode> {
        val partyNodes = (1..numberOfNodes).map { current ->
            val char = current.toChar() + 64
            val name = CordaX500Name("Party$char", "London", "GB")
            network.createPartyNode(name)
        }
        return partyNodes
    }

    fun TestStartedNode.legalIdentity() = info.legalIdentities.first()

    /** Create a new obligation with the supplied parameters. */
    fun <T : TokenizableAssetInfo> TestStartedNode.createObligation(
            amount: Amount<T>,
            counterparty: TestStartedNode,
            role: CreateObligation.InitiatorRole
    ): CordaFuture<SignedTransaction> {
        val flow = CreateObligation.Initiator(amount, role, counterparty.legalIdentity())
        return services.startFlow(flow).resultFuture
    }

    /** Add settlement instructions to existing obligation. */
    fun TestStartedNode.addSettlementInstructions(
            linearId: UniqueIdentifier,
            settlementInstructions: SettlementInstructions
    ): CordaFuture<SignedTransaction> {
        val flow = AddSettlementInstructions(linearId, settlementInstructions)
        return services.startFlow(flow).resultFuture
    }

    /** Add settlement instructions to existing obligation. */
    fun TestStartedNode.makePayment(linearId: UniqueIdentifier): CordaFuture<SignedTransaction> {
        return services.startFlow(SettleObligation(linearId)).resultFuture
    }

    /** From a transaction which produces a single output, retrieve that output. */
    inline fun <reified T : LinearState> SignedTransaction.singleOutput() = tx.outRefsOfType<T>().single()

    inline fun <reified T : LinearState> StateAndRef<T>.linearId() = state.data.linearId

    fun TestStartedNode.queryObligationById(linearId: UniqueIdentifier): StateAndRef<Obligation.State<DigitalCurrency>> {
        val query = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))
        return services.vaultService.queryBy<Obligation.State<DigitalCurrency>>(query).states.single()
    }

    /** Check to see if a node recorded a transaction with a particular hash. Return a future signed transaction. */
    fun TestStartedNode.watchForTransaction(txId: SecureHash): CordaFuture<SignedTransaction> {
        return services.validatedTransactions.updates.filter { it.id == txId }.toFuture()
    }

//    /** Create and sign a tx containing a dummy linear state as output, return a future signed transaction. */
//    fun TestStartedNode.createDummyTransaction(): CordaFuture<SignedTransaction> {
//        val me = services.myInfo.legalIdentities.first()
//        val tx = TransactionBuilder(services.networkMapCache.notaryIdentities.first()).apply {
//            val state = DummyLinearContract.State(participants = listOf(me))
//            addOutputState(state, DUMMY_LINEAR_CONTRACT_PROGRAM_ID)
//            addCommand(DummyCommandData, listOf(me.owningKey))
//        }
//        return services.startFlow(FinalityFlow(services.signInitialTransaction(tx))).resultFuture
//    }


}