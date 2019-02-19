package com.r3.corda.finance.swift.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.finance.obligation.client.flows.MakeOffLedgerPayment
import com.r3.corda.finance.obligation.states.Obligation
import com.r3.corda.finance.obligation.types.FiatCurrency
import com.r3.corda.finance.obligation.types.Money
import com.r3.corda.finance.obligation.types.OffLedgerPayment
import com.r3.corda.finance.obligation.types.Payment
import com.r3.corda.finance.obligation.types.PaymentStatus
import com.r3.corda.finance.swift.services.SWIFTService
import com.r3.corda.finance.swift.types.SWIFTPaymentResponse
import com.r3.corda.finance.swift.types.SwiftPayment
import com.r3.corda.finance.swift.types.SwiftSettlement
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowException
import net.corda.core.utilities.ProgressTracker
import java.time.Duration
import java.util.*

class MakeSWIFTPayment<T : Money>(
        amount: Amount<T>,
        obligationStateAndRef: StateAndRef<Obligation<*>>,
        settlementMethod: OffLedgerPayment<*>,
        progressTracker: ProgressTracker
) : MakeOffLedgerPayment<T>(amount, obligationStateAndRef, settlementMethod, progressTracker) {

    /** Don't want to serialize this. */
    private fun createAndSignAndSubmitPayment(obligation: Obligation<*>, amount: Amount<T>): SWIFTPaymentResponse {
        val swiftService = serviceHub.cordaService(SWIFTService::class.java)
        val swiftClient = swiftService.swiftClient()

        if (obligation.dueBy == null)
            throw FlowException("Due date must be provided for SWIFT payment")

        if (amount.token !is FiatCurrency)
            throw FlowException("Amount for SWIFT payment must be in FiatCurrency")

        if (obligation.settlementMethod == null || obligation.settlementMethod !is SwiftSettlement)
            throw FlowException("settlementMethod of SwiftSettlement must be provided for SWIFT payment")

        val swiftSettlement = obligation.settlementMethod as SwiftSettlement

        return swiftClient.makePayment(
                // TODO: for now we taking obligations's linearId as an e2e payment id. This behaviour needs to be changed,
                // we need to let API consumers to provide their own e2e ids as strings, which would also give us idempotence out-of-the-box
                obligation.linearId.toString(),
                Date.from(obligation.dueBy),
                amount as Amount<FiatCurrency>,
                swiftService.debtorName,
                swiftService.debtorLei,
                swiftService.debtorIban,
                swiftService.debtorBicfi,
                swiftSettlement.creditorName,
                swiftSettlement.creditorLei,
                swiftSettlement.accountToPay,
                swiftSettlement.creditorBicfi,
                swiftSettlement.remittanceInformation
        )
    }

    @Suspendable
    override fun setup() {
    }

    override fun checkBalance(requiredAmount: Amount<*>) {
    }

    @Suspendable
    override fun makePayment(obligation: Obligation<*>, amount: Amount<T>): Payment<T> {
        if (amount.token !is FiatCurrency)
            throw FlowException("SWIFT payment amount must be in FiatCurrency")
        val paymentResponse = createAndSignAndSubmitPayment(obligation, amount)
        val paymentReference = paymentResponse.uetr
        sleep(Duration.ofMillis(1))
        return SwiftPayment(paymentReference, amount as Amount<FiatCurrency>, PaymentStatus.SENT) as Payment<T>
    }
}