package net.corda.finance.obligation.oracle.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.obligation.contracts.Obligation
import net.corda.finance.obligation.flows.AbstractSendToSettlementOracle
import net.corda.finance.obligation.oracle.services.RippleOracleService
import net.corda.finance.obligation.types.DigitalCurrency
import net.corda.finance.obligation.types.PaymentStatus
import net.corda.finance.ripple.types.XRPSettlementInstructions
import java.time.Duration

@InitiatedBy(AbstractSendToSettlementOracle::class)
class VerifySettlement(val otherSession: FlowSession) : FlowLogic<Unit>() {

    override val progressTracker: ProgressTracker = ProgressTracker()

    enum class VerifyResult { TIMEOUT, SUCCESS, PENDING }

    @Suspendable
    fun verifySettlement(obligation: Obligation.State<DigitalCurrency>, settlementInstructions: XRPSettlementInstructions): VerifyResult {
        val oracleService = serviceHub.cordaService(RippleOracleService::class.java)
        while (true) {
            logger.info("Checking for settlement...")
            val result = oracleService.hasPaymentSettled(settlementInstructions, obligation)
            when (result) {
                VerifyResult.SUCCESS, VerifyResult.TIMEOUT -> return result
                // Sleep for five seconds before we try again. The Oracle might receive the request to verify payment
                // before the payment succeed. Also it takes a bit of time for all the nodes to receive the new ledger
                // version. Note: sleep is a suspendable operation.
                VerifyResult.PENDING -> sleep(Duration.ofSeconds(5))
            }
        }
    }

    @Suspendable
    override fun call() {
        // 1. Receive the obligation state we are verifying settlement of.
        val obligationStateAndRef = subFlow(ReceiveStateAndRefFlow<Obligation.State<DigitalCurrency>>(otherSession)).single()
        val obligation = obligationStateAndRef.state.data
        val settlementInstructions = obligation.settlementInstructions

        // 2. Check there are settlement instructions.
        check(settlementInstructions != null) { "This obligation has no settlement instructions." }

        // 3. Handle different settlement methods.
        val verifyResult = when (settlementInstructions) {
            is XRPSettlementInstructions -> verifySettlement(obligation, settlementInstructions)
            else -> throw IllegalStateException("This Oracle only handles XRP settlement.")
        }

        if (verifyResult == VerifyResult.TIMEOUT) {
            println("TIMEOUT")
            return
        }

        // 4. Update settlement instructions.
        // For now we cannot partially settle the obligation.
        val updatedSettlementInstructions = settlementInstructions.updateStatus(PaymentStatus.ACCEPTED)
        val obligationWithUpdatedStatus = obligation.apply {
            withSettlementTerms(updatedSettlementInstructions)
            settle(faceAmount)
        }

        // 4. Build transaction.
        // Change the payment status to accepted - this means that the obligation has settled.
        val signingKey = ourIdentity.owningKey
        val notary = serviceHub.networkMapCache.notaryIdentities.firstOrNull()
                ?: throw FlowException("No available notary.")
        val utx = TransactionBuilder(notary = notary).apply {
            addInputState(obligationStateAndRef)
            addCommand(Obligation.Commands.Extinguish(), signingKey)
            addOutputState(obligationWithUpdatedStatus, Obligation.CONTRACT_REF)
        }

        // 5. Sign transaction.
        val stx = serviceHub.signInitialTransaction(utx, signingKey)

        // 6. Finalise transaction and send to participants.
        otherSession.send(stx)
    }
}
