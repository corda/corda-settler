package com.r3.corda.finance.obligation.oracle.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.finance.obligation.contracts.ObligationContract
import com.r3.corda.finance.obligation.contracts.commands.ObligationCommands
import com.r3.corda.finance.obligation.contracts.flows.AbstractSendToSettlementOracle
import com.r3.corda.finance.obligation.contracts.states.Obligation
import com.r3.corda.finance.obligation.contracts.types.PaymentStatus
import com.r3.corda.finance.obligation.contracts.types.SettlementOracleResult
import com.r3.corda.finance.obligation.oracle.services.XrpOracleService
import com.r3.corda.finance.ripple.types.XrpPayment
import com.r3.corda.finance.ripple.types.XrpSettlement
import com.r3.corda.finance.swift.services.SWIFTService
import com.r3.corda.finance.swift.types.SWIFTPaymentStatusType
import com.r3.corda.finance.swift.types.SwiftPayment
import com.r3.corda.finance.swift.types.SwiftSettlement
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.*
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import java.time.Duration

@Suppress("UNCHECKED_CAST")
@InitiatedBy(AbstractSendToSettlementOracle::class)
class VerifySettlement(private val otherSession: FlowSession) : FlowLogic<Unit>() {

    override val progressTracker: ProgressTracker = ProgressTracker()
    companion object {
        private const val INITIAL_TIME_TO_WAIT_FOR_SETTLEMENT = 3L
        private const val RETRY_TIME_TO_WAIT_FOR_SETTLEMENT = 5L
    }


    enum class VerifyResult { TIMEOUT, SUCCESS, PENDING, REJECTED }

    @Suspendable
    fun verifyXrpSettlement(obligation: Obligation<TokenType>, xrpPayment: XrpPayment<TokenType>): VerifyResult {
        val oracleService = serviceHub.cordaService(XrpOracleService::class.java)
        // We wait for a couple of sessions before checking for settlement (The ripple nodes need to catch-up).
        sleep(Duration.ofSeconds(INITIAL_TIME_TO_WAIT_FOR_SETTLEMENT))
        while (true) {
            logger.info("Checking for settlement...")
            val result = oracleService.hasPaymentSettled(xrpPayment, obligation)
            when (result) {
                VerifyResult.SUCCESS, VerifyResult.TIMEOUT, VerifyResult.REJECTED -> return result
                // Sleep for five seconds before we try again. The Oracle might receive the request to verify payment
                // before the payment succeed. Also it takes a bit of time for all the nodes to receive the new ledger
                // version. Note: sleep is a suspendable operation.
                VerifyResult.PENDING -> sleep(Duration.ofSeconds(RETRY_TIME_TO_WAIT_FOR_SETTLEMENT))
            }
        }
    }

    @Suspendable
    fun verifySwiftSettlement(swiftPayment: SwiftPayment<TokenType>): VerifyResult {
        val oracleService = serviceHub.cordaService(SWIFTService::class.java)
        while (true) {
            val paymentStatus = oracleService.swiftClient().getPaymentStatus(swiftPayment.paymentReference)
            when (paymentStatus.transactionStatus.status) {
                SWIFTPaymentStatusType.RJCT -> return VerifyResult.REJECTED
                SWIFTPaymentStatusType.ACCC -> return VerifyResult.SUCCESS
                // TODO: we need to come up with some more clever way of waiting for the status to be updated. Maybe exponential back-off
                SWIFTPaymentStatusType.ACSP -> sleep(Duration.ofSeconds(RETRY_TIME_TO_WAIT_FOR_SETTLEMENT))
                else -> throw FlowException("Invalid payment status ${paymentStatus.transactionStatus}")
            }
        }
    }

    private fun createTransaction(
            obligationStateAndRef: StateAndRef<Obligation<TokenType>>,
            status: PaymentStatus
    ): SignedTransaction {
        // Update payment status.
        val obligation = obligationStateAndRef.state.data
        // Status is MUTABLE to save us having to re-create the payments list.
        val payment = obligation.payments.last()
        payment.status = status
        // Create transaction.
        val signingKey = ourIdentity.owningKey
        val notary = serviceHub.networkMapCache.notaryIdentities.firstOrNull()
                ?: throw FlowException("No available notary.")
        val utx = TransactionBuilder(notary = notary).apply {
            addInputState(obligationStateAndRef)
            addCommand(ObligationCommands.UpdatePayment(payment.paymentReference), signingKey)
            addOutputState(obligation, ObligationContract.CONTRACT_REF)
        }

        // 5. Sign transaction.
        return serviceHub.signInitialTransaction(utx, signingKey)
    }

    @Suspendable
    override fun call() {
        // 1. Receive the obligation state we are verifying settlement of.
        val obligationStateAndRef = subFlow(ReceiveStateAndRefFlow<Obligation<TokenType>>(otherSession)).single()
        val obligation = obligationStateAndRef.state.data
        val settlementMethod = obligation.settlementMethod

        // 2. Check there are settlement instructions.
        if (settlementMethod == null) {
            otherSession.send(SettlementOracleResult.Failure(null, "The obligation has no settlement method." ))
            return
        }

        // 3. As payments are appended to the end of the payments list, we assume we are only checking the last
        // payment. The obligation is sent to the settlement Oracle for EACH payment, so everyone does get checked.
        val payments = obligation.payments
        val lastPayment = if (payments.isEmpty()) {
            otherSession.send(SettlementOracleResult.Failure(null, "No payments have been made for this obligation."))
            return
        } else obligation.payments.last()

        // 4. Handle different settlement methods.
        val verifyResult = when (settlementMethod) {
            is XrpSettlement -> verifyXrpSettlement(obligation, lastPayment as XrpPayment<TokenType>)
            is SwiftSettlement -> verifySwiftSettlement(lastPayment as SwiftPayment)
            else -> throw IllegalStateException("Invalid settlement method $settlementMethod.")
        }

        when (verifyResult) {
            VerifyResult.TIMEOUT, VerifyResult.REJECTED -> {
                val stx = createTransaction(obligationStateAndRef, PaymentStatus.FAILED)
                otherSession.send(SettlementOracleResult.Failure(stx, "Payment wasn't made by the deadline."))
            }
            VerifyResult.SUCCESS -> {
                val stx = createTransaction(obligationStateAndRef, PaymentStatus.SETTLED)
                otherSession.send(SettlementOracleResult.Success(stx))
            }
            else -> throw IllegalStateException("This shouldn't happen!")
        }
    }
}
