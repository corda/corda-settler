package com.r3.corda.finance.obligation.oracle.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.finance.obligation.DigitalCurrency
import com.r3.corda.finance.obligation.PaymentStatus
import com.r3.corda.finance.obligation.contracts.Obligation
import com.r3.corda.finance.obligation.flows.AbstractSendToSettlementOracle
import com.r3.corda.finance.obligation.flows.OracleResult
import com.r3.corda.finance.obligation.oracle.services.XrpOracleService
import com.r3.corda.finance.ripple.types.XRPSettlementInstructions
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.time.Duration

@InitiatedBy(AbstractSendToSettlementOracle::class)
class VerifySettlement(val otherSession: FlowSession) : FlowLogic<Unit>() {

    override val progressTracker: ProgressTracker = ProgressTracker()

    enum class VerifyResult { TIMEOUT, SUCCESS, PENDING }

    @Suspendable
    fun verifyXrpSettlement(obligation: Obligation.State<DigitalCurrency>, settlementInstructions: XRPSettlementInstructions): VerifyResult {
        val oracleService = serviceHub.cordaService(XrpOracleService::class.java)
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

    private fun createTransaction(obligationStateAndRef: StateAndRef<Obligation.State<DigitalCurrency>>): SignedTransaction {
        val obligation = obligationStateAndRef.state.data
        val settlementInstructions = obligation.settlementInstructions as XRPSettlementInstructions

        // 4. Update settlement instructions.
        // For now we cannot partially settle the obligation.
        val updatedSettlementInstructions = settlementInstructions.updateStatus(PaymentStatus.ACCEPTED)
        val obligationWithUpdatedStatus = obligation
                .withSettlementTerms(updatedSettlementInstructions)
                .settle(obligation.faceAmount)

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
        return serviceHub.signInitialTransaction(utx, signingKey)
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
            is XRPSettlementInstructions -> verifyXrpSettlement(obligation, settlementInstructions)
            else -> throw IllegalStateException("This Oracle only handles XRP settlement.")
        }

        when (verifyResult) {
            VerifyResult.TIMEOUT -> {
                otherSession.send(OracleResult.Failure("Payment wasn't made by the deadline."))
                return
            }
            VerifyResult.SUCCESS -> {
                val stx = createTransaction(obligationStateAndRef)
                // 6. Finalise transaction and send to participants.
                otherSession.send(OracleResult.Success(stx))
            }
            else -> throw IllegalStateException("This shouldn't happen!")
        }
    }
}
