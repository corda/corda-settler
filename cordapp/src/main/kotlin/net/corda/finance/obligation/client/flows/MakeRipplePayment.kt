package net.corda.finance.obligation.client.flows

import co.paralleluniverse.fibers.Suspendable
import com.ripple.core.coretypes.AccountID
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FinalityFlow
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.SendTransactionFlow
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker
import net.corda.finance.obligation.client.DEFAULT_RIPPLE_FEE
import net.corda.finance.obligation.client.contracts.Obligation
import net.corda.finance.obligation.client.toRippleAmount
import net.corda.finance.obligation.client.toRippleHash
import net.corda.finance.obligation.client.types.RippleSettlementInstructions
import net.corda.finance.ripple.RippleClientForPayment
import net.corda.finance.ripple.types.SubmitPaymentResponse
import com.ripple.core.coretypes.Amount as RippleAmount

/*

For testing...

Address     ra6mzL1Xy9aN5eRdjzn9CHTMwcczG1uMpN
Secret      sasKgJbTbka3ahFew2BZybfNg494C
Balance     10,000 XRP

Address     rNmkj4AtjEHJh3D9hMRC4rS3CXQ9mX4S4b
Secret      ssn8cYYksFFexYq97sw9UnvLnMKgh
Balance     10,000 XRP
 */
class MakeRipplePayment(val obligationStateAndRef: StateAndRef<Obligation.State<*>>) : FlowLogic<SignedTransaction>() {

    override val progressTracker: ProgressTracker = tracker()

    companion object {
        @JvmStatic
        val rippleClient = RippleClientForPayment("ripple.conf")

        object INITIALISING : ProgressTracker.Step("Performing initial steps.")
        object CHECKING_BALANCE : ProgressTracker.Step("Checking for sufficient XRP balance.")
        object MAKING_PAYMENT : ProgressTracker.Step("Making XRP payment.")
        object BUILDING : ProgressTracker.Step("Building and verifying Corda transaction.")
        object SIGNING : ProgressTracker.Step("signing transaction.")

        object FINALISING : ProgressTracker.Step("Finalising transaction.") {
            override fun childProgressTracker() = FinalityFlow.tracker()
        }

        object ORACLE : ProgressTracker.Step("Sending to settlement Oracle.")

        fun tracker() = ProgressTracker(INITIALISING, CHECKING_BALANCE, MAKING_PAYMENT, BUILDING, SIGNING, FINALISING, ORACLE)
    }

    private fun checkBalance(requiredAmount: Amount<*>) {
        val ourAccountInfo = rippleClient.accountInfo(rippleClient.address)
        val balance = ourAccountInfo.accountData.balance
        println(balance)
        println(requiredAmount.toRippleAmount())
        println(requiredAmount)
        check(balance > requiredAmount.toRippleAmount()) {
            "You do not have enough XRP to make the payment."
        }
    }

    // We don't want to serialise any of this stuff.
    private fun makePayment(account: AccountID, obligation: Obligation.State<*>): SubmitPaymentResponse {
        // 1. Create a new payment.
        val payment = rippleClient.createPayment(
                source = rippleClient.address,
                destination = account,
                amount = obligation.amount.toRippleAmount(),
                fee = DEFAULT_RIPPLE_FEE,
                linearId = SecureHash.sha256(obligation.linearId.id.toString()).toRippleHash()
        )

        // 2. Sign and submit the payment.
        val signedPayment = rippleClient.signPayment(payment)

        logger.info("Serialised Ripple payment: ${payment.toJSON()}")
        logger.info("Serialised Ripple signed transaction: ${signedPayment.tx_blob}")

        return rippleClient.submitTransaction(signedPayment)
    }

    @Suspendable
    override fun call(): SignedTransaction {
        // 1. This flow should only be started by the beneficiary.
        progressTracker.currentStep = INITIALISING

        val obligation = obligationStateAndRef.state.data
        val obligor = obligation.withWellKnownIdentities(serviceHub).obligor
        check(ourIdentity == obligor) { "This flow can only be started by the obligee. " }

        // 2. Get settlement instructions.
        val settlementInstructions = obligation.settlementInstructions as RippleSettlementInstructions
        val accountToPay = settlementInstructions.accountToPay

        // Check that the specified account has enough XRP to pay.
        progressTracker.currentStep = CHECKING_BALANCE
        checkBalance(obligation.amount)

        // 3. Check that the payment hasn't already been made.
        check(settlementInstructions.rippleTransactionHash == null) {
            "A ripple payment payment has already been made."
        }

        // 4. Make payment.
        progressTracker.currentStep = MAKING_PAYMENT
        val response = makePayment(accountToPay, obligation)

        // 5. Add payment hash to settlement instructions.
        val paymentHash = SecureHash.parse(response.txJson.hash)
        val updatedSettlementInstructions = settlementInstructions.addRippleTransactionHash(paymentHash)
        val obligationWithUpdatedSettlementInstructions = obligation.withSettlementTerms(updatedSettlementInstructions)

        // 6. Add updated settlement terms to obligation.
        progressTracker.currentStep = BUILDING
        val signingKey = listOf(obligation.obligor.owningKey)
        val notary = serviceHub.networkMapCache.notaryIdentities.firstOrNull()
                ?: throw FlowException("No available notary.")
        val utx = TransactionBuilder(notary = notary).apply {
            addInputState(obligationStateAndRef)
            addOutputState(obligationWithUpdatedSettlementInstructions, Obligation.CONTRACT_REF)
            addCommand(Obligation.Commands.AddPaymentDetails(), signingKey)
        }

        // 7. Sign transaction.
        progressTracker.currentStep = SIGNING
        val stx = serviceHub.signInitialTransaction(utx, signingKey)

        // 8. Finalise transaction and send to participants.
        progressTracker.currentStep = FINALISING
        val ftx = subFlow(FinalityFlow(stx, FINALISING.childProgressTracker()))

        // 9. Send transaction to Oracle.
        // TODO: Is this really what we want to do?
        val session = initiateFlow(settlementInstructions.settlementOracle)
        subFlow(SendTransactionFlow(session, stx))

        return ftx
    }

}