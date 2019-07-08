package com.r3.corda.finance.manual.flows

import co.paralleluniverse.fibers.Suspendable
import com.r3.corda.finance.manual.types.ManualPayment
import com.r3.corda.finance.manual.types.ManualSettlement
import com.r3.corda.finance.obligation.contracts.states.Obligation
import com.r3.corda.finance.obligation.contracts.types.OffLedgerPayment
import com.r3.corda.finance.obligation.contracts.types.Payment
import com.r3.corda.finance.obligation.contracts.types.PaymentStatus
import com.r3.corda.finance.obligation.workflows.flows.MakeOffLedgerPayment
import com.r3.corda.lib.tokens.contracts.types.TokenType
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowException
import net.corda.core.utilities.ProgressTracker

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
        if (obligation.settlementMethod == null || obligation.settlementMethod !is ManualSettlement)
            throw FlowException("settlementMethod of ManualSettlement must be provided for manual payment")
        return ManualPayment((obligation.payments.size + 1).toString(), amount as Amount<TokenType>, PaymentStatus.SENT) as Payment<T>
    }
}