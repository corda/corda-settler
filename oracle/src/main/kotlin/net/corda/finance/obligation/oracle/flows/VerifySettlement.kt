package net.corda.finance.obligation.oracle.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.obligation.contracts.Obligation
import net.corda.finance.obligation.flows.AbstractSendToSettlementOracle
import net.corda.finance.obligation.oracle.services.RippleOracleService
import net.corda.finance.ripple.types.RippleSettlementInstructions
import java.time.Duration

@InitiatedBy(AbstractSendToSettlementOracle::class)
class VerifySettlement(val otherSession: FlowSession) : FlowLogic<Unit>() {

    override val progressTracker: ProgressTracker = ProgressTracker()

    private fun handleRippleSettlement(obligation: Obligation.State<*>, settlementInstructions: RippleSettlementInstructions) {
        val oracleService = serviceHub.cordaService(RippleOracleService::class.java)
        while (true) {
            logger.info("Checking for settlement...")
            val hasPaymentSettled = oracleService.hasPaymentSettled(settlementInstructions, obligation)
            if (hasPaymentSettled) break
            // Sleep for five seconds before we try again.
            sleep(Duration.ofSeconds(5))
        }
    }

    @Suspendable
    override fun call() {
        // 1. Receive the obligation state we are verifying settlement of.
        val obligationStateAndRef = subFlow(ReceiveStateAndRefFlow<Obligation.State<*>>(otherSideSession = otherSession)).single()
        val obligation = obligationStateAndRef.state.data
        val settlementInstructions = obligation.settlementInstructions

        // 2. Check there are settlement instructions.
        check(settlementInstructions != null) { "This obligation has no settlement instructions." }

        // 3. Handle different settlement methods.
        when (settlementInstructions) {
            is RippleSettlementInstructions -> handleRippleSettlement(obligation, settlementInstructions)
            else -> throw IllegalStateException("This Oracle only handles Ripple settlement.")
        }

        // 4. Build transaction.
        val signingKey = ourIdentity.owningKey
        val notary = serviceHub.networkMapCache.notaryIdentities.firstOrNull()
                ?: throw FlowException("No available notary.")
        val utx = TransactionBuilder(notary = notary).apply {
            addInputState(obligationStateAndRef)
            addCommand(Obligation.Commands.Extinguish(), signingKey)
        }

        // 5. Sign transaction.
        val stx = serviceHub.signInitialTransaction(utx, signingKey)

        // 6. Finalise transaction and send to participants.
        otherSession.send(stx)
    }

}