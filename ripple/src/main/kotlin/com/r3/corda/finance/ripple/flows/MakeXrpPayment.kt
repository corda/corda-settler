package com.r3.corda.finance.ripple.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.finance.obligation.USD
import com.r3.corda.finance.obligation.types.Money
import com.r3.corda.finance.obligation.client.flows.MakeOffLedgerPayment
import com.r3.corda.finance.obligation.states.Obligation
import com.r3.corda.finance.obligation.types.OffLedgerPayment
import com.r3.corda.finance.obligation.types.PaymentStatus
import com.r3.corda.finance.ripple.services.XRPService
import com.r3.corda.finance.ripple.types.*
import com.r3.corda.finance.ripple.utilities.DEFAULT_XRP_FEE
import com.r3.corda.finance.ripple.utilities.XRP
import com.r3.corda.finance.ripple.utilities.toXRPAmount
import com.r3.corda.finance.ripple.utilities.toXRPHash
import com.ripple.core.coretypes.uint.UInt32
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowException
import net.corda.core.utilities.ProgressTracker
import java.math.BigDecimal
import java.time.Duration
import com.ripple.core.coretypes.Amount as RippleAmount


// For testing.
//
// Address     ra6mzL1Xy9aN5eRdjzn9CHTMwcczG1uMpN
// Secret      sasKgJbTbka3ahFew2BZybfNg494C
// Balance     10,000 XRP
//
// Address     rNmkj4AtjEHJh3D9hMRC4rS3CXQ9mX4S4b
// Secret      ssn8cYYksFFexYq97sw9UnvLnMKgh
// Balance     10,000 XRP

class MakeXrpPayment<T : Money>(
        amount: Amount<T>,
        obligationStateAndRef: StateAndRef<Obligation<*>>,
        settlementMethod: OffLedgerPayment<*>,
        progressTracker: ProgressTracker
) : MakeOffLedgerPayment<T>(amount, obligationStateAndRef, settlementMethod, progressTracker) {

    /** Ensures that the flow uses the same sequence number for idempotency. */
    var seqNo: UInt32? = null

    /** Assuming each ledger takes 5 seconds, or so, we are willing to wait 60 seconds for the payment to "arrive". */
    val waitingPeriod: Long = 60 / 5

    /** Don't want to serialize this. */
    private fun getSequenceNumber(): UInt32 {
        val xrpService = serviceHub.cordaService(XRPService::class.java).client
        val accountId = xrpService.address
        return xrpService.nextSequenceNumber(accountId)
    }

    /** Don't want to serialize this either. */
    private fun getCurrentLedgerIndex(): Long {
        val xrpService = serviceHub.cordaService(XRPService::class.java).client
        return xrpService.ledgerIndex().ledgerCurrentIndex
    }

    /** Don't want to serialize this. */
    private fun createAndSignAndSubmitPayment(obligation: Obligation<*>, amount: Amount<T>): SubmitPaymentResponse {
        val xrpService = serviceHub.cordaService(XRPService::class.java).client
        // 1. Create a new payment.
        // This function will always use the sequence number which was obtained before the flow check-point.
        // So if this flow is restarted and the payment were to be made twice then the Ripple node will return an error
        // because the same sequence number will be used twice.
        val payment = xrpService.createPayment(
                // Always use the sequence number provided. It will never be null at this point.
                sequence = seqNo!!,
                source = xrpService.address,
                destination = (settlementMethod as XrpSettlement).accountToPay,
                amount = amount.toXRPAmount(),
                fee = DEFAULT_XRP_FEE,
                linearId = SecureHash.sha256(obligation.linearId.id.toString()).toXRPHash()
        )

        // 2. Sign the payment.
        val signedPayment = xrpService.signPayment(payment)
        return xrpService.submitTransaction(signedPayment)
    }

    @Suspendable
    override fun setup() {
        // Get a new sequence number.
        seqNo = getSequenceNumber()
        // Checkpoint the flow here.
        // - If the flow dies before payment, the payment should still happen.
        // - If the flow dies after payment and is replayed from this point, then the second payment will fail.
        sleep(Duration.ofMillis(1))
    }

    override fun checkBalance(requiredAmount: Amount<*>) {
        // Get a XRPService client.
        val xrpClient = serviceHub.cordaService(XRPService::class.java).client
        // Check the balance on the supplied XRPService address.
        val ourAccountInfo = xrpClient.accountInfo(xrpClient.address)
        val balance = ourAccountInfo.accountData.balance
        // XRP accounts must contain a minimum of 20 XRP.
        check(balance > requiredAmount.toXRPAmount().add(20)) {
            "You do not have enough XRP to make the payment. Needed: $requiredAmount, " +
                    "available: $balance"
        }
    }

    @Suspendable
    override fun makePayment(obligation: Obligation<*>, amount: Amount<T>): XrpPayment<T> {
        // Create, sign and submit a payment request then store the transaction hash and checkpoint.
        // Fail if there is any exception as
        val paymentResponse = try {
            createAndSignAndSubmitPayment(obligation, amount)
        } catch (e: AlreadysubmittedException) {
            logger.warn(e.message)
            throw FlowException("The transaction was already submitted. However, " +
                    "the node failed before check-pointing the transaction hash. " +
                    "Please check your XRP payments to obtain the transaction hash " +
                    "so you can update the obligation state with the payment reference " +
                    "by starting the UpdateObligationWithPayment flow.")
        } catch (e: IncorrectSequenceNumberException) {
            logger.warn(e.message)
            throw FlowException("An incorrect sequence number was used. This could be " +
                    "due to a race with another application to submit a ripple transaction." +
                    "Restarting this the MakeOffLedgerPayment flow for the same obligation" +
                    "should fix this.")
        }

        // Check-point once we have stored the payment reference.
        // If the flow fails from this point, we will just return the payment reference
        val paymentReference = paymentResponse.txJson.hash
        sleep(Duration.ofMillis(1))

        // Return the payment information.
        val lastLedgerIndex = getCurrentLedgerIndex() + waitingPeriod
        return XrpPayment(paymentReference, lastLedgerIndex, amount, PaymentStatus.SENT)
    }
}