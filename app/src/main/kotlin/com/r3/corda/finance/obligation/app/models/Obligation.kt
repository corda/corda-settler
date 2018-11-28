package com.r3.corda.finance.obligation.app.models

import com.r3.corda.finance.obligation.states.Obligation
import com.r3.corda.finance.obligation.types.Payment
import com.r3.corda.finance.obligation.types.SettlementMethod
import javafx.beans.property.*
import javafx.collections.ObservableList
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import tornadofx.*
import java.time.Instant

class UiObligation(
        linearId: UniqueIdentifier,
        faceAmount: Amount<*>,
        obligor: Party,
        obligee: Party,
        settlementStatus: Obligation.SettlementStatus,
        createdAt: Instant? = null,
        dueBy: Instant? = null,
        settlementMethod: SettlementMethod? = null,
        hasSettlementMethod: Boolean = false,
        hasPayments: Boolean = false,
        payments: List<Payment<*>> = emptyList()
) {

    val linearIdProperty = SimpleObjectProperty<UniqueIdentifier>(this, "linearId", linearId)
    var linearId by linearIdProperty

    val amountProperty = SimpleObjectProperty<Amount<*>>(this, "amount", faceAmount)
    var amount by amountProperty

    val obligorProperty = SimpleObjectProperty<Party>(this, "obligor", obligor)
    var obligor by obligorProperty

    val obligeeProperty = SimpleObjectProperty<Party>(this, "obligee", obligee)
    var obligee by obligeeProperty

    val statusProperty = SimpleObjectProperty<Obligation.SettlementStatus>(this, "settlementStatus", settlementStatus)
    var settlementStatus by statusProperty

    val createdAtProperty = SimpleObjectProperty<Instant>(this, "createdAt", createdAt)
    var createdAt by createdAtProperty

    val dueByProperty = SimpleObjectProperty<Instant>(this, "dueBy", dueBy)
    var dueBy by dueByProperty

    val settlementMethodProperty = SimpleObjectProperty<SettlementMethod>(this, "settlementMethod", settlementMethod)
    var settlementMethod by settlementMethodProperty

    val hasSettlementMethodProperty = SimpleBooleanProperty(this, "hasSettlementMethod", hasSettlementMethod)
    var hasSettlementMethod by hasSettlementMethodProperty

    val hasPaymentsProperty = SimpleBooleanProperty(this, "hasPayments", hasPayments)
    var hasPayments by hasPaymentsProperty

    val paymentsProperty = SimpleListProperty<Payment<*>>(this, "payments", payments.observable())
    var payments by paymentsProperty
}

class ObligationModel : ItemViewModel<UiObligation>() {
    val linearId = bind(UiObligation::linearIdProperty)
    val amount = bind(UiObligation::amountProperty)
//    val paid = bind(ObligationContract::role)
    val obligor = bind(UiObligation::obligorProperty)
    val obligee = bind(UiObligation::obligeeProperty)
    val settlementStatus = bind(UiObligation::statusProperty)
    val createdAt = bind(UiObligation::createdAtProperty)
    val dueBy = bind(UiObligation::dueByProperty)
    val settlementMethod = bind(UiObligation::settlementMethodProperty)
    val hasSettlementMethod = bind(UiObligation::hasSettlementMethodProperty)
    val payments = bind(UiObligation::paymentsProperty)
    val hasPayments = bind(UiObligation::hasPaymentsProperty)
}