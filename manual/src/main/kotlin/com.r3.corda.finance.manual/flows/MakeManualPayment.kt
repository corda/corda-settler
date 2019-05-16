package com.r3.corda.finance.manual.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.finance.manual.types.ManualPayment
import com.r3.corda.finance.manual.types.ManualSettlement
import com.r3.corda.finance.obligation.client.flows.MakeOffLedgerPayment
import com.r3.corda.finance.obligation.states.Obligation
import com.r3.corda.finance.obligation.types.OffLedgerPayment
import com.r3.corda.finance.obligation.types.Payment
import com.r3.corda.finance.obligation.types.PaymentStatus
import com.r3.corda.sdk.token.contracts.types.TokenType
import com.r3.corda.sdk.token.money.FiatCurrency
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowException
import net.corda.core.utilities.ProgressTracker
import java.time.Duration

class MakeManualPayment<T : TokenType>(
        amount: Amount<T>,
        obligationStateAndRef: StateAndRef<Obligation<*>>,
        settlementMethod: OffLedgerPayment<*>,
        progressTracker: ProgressTracker
) : MakeOffLedgerPayment<T>(amount, obligationStateAndRef, settlementMethod, progressTracker) {

    @Suspendable
    override fun setup() {
    }

    override fun checkBalance(requiredAmount: Amount<*>) {
    }

    @Suspendable
    override fun makePayment(obligation: Obligation<*>, amount: Amount<T>): Payment<T> {
        if (amount.token !is FiatCurrency)
            throw FlowException("Manual payment amount must be in FiatCurrency")
        if (obligation.settlementMethod == null || obligation.settlementMethod !is ManualSettlement)
            throw FlowException("settlementMethod of ManualSettlement must be provided for manual payment")
        sleep(Duration.ofMillis(1))
        return ManualPayment((obligation.payments.size + 1).toString(), amount as Amount<FiatCurrency>, PaymentStatus.SENT) as Payment<T>
    }
}