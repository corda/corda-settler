package com.r3.corda.finance.obligation.app.models

import com.r3.corda.finance.obligation.ObligationStatus
import com.r3.corda.finance.obligation.SettlementInstructions
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import tornadofx.*
import com.r3.corda.finance.obligation.contracts.Obligation as ObligationContract

class SettlementInstructions() {

}

class SettlementInstructionsModel() {

}

enum class Role { OBLIGOR, OBLIGEE }

/**
 * We are ignoring the "paid" property since obligations are either outstanding or fully paid. We are adding a "role"
 * property to indicate which party is the obligor/obligee.
 */
class Obligation<T : Any>(
        linearId: UniqueIdentifier,
        faceAmount: Amount<T>,
        counterparty: Party,
        role: Role,
        status: ObligationStatus,
        settlementInstructions: SettlementInstructions?
) {

    val linearIdProperty = SimpleStringProperty()
    var linearId by linearIdProperty

    val amountProperty = SimpleObjectProperty<Amount<T>>()
    var amount by amountProperty

    val roleProperty = SimpleObjectProperty<Role>()
    var role by roleProperty

    val counterpartyProperty = SimpleStringProperty()
    var counterparty by counterpartyProperty

    val statusProperty = SimpleStringProperty()
    var status by statusProperty

    val settlementInstructionsProperty = SimpleObjectProperty<SettlementInstructions>()
    var settlementInstructions by settlementInstructionsProperty
}

class ObligationModel<T : Any> : ItemViewModel<Obligation<T>>() {
    val linearId = bind(Obligation<T>::linearId)
    val amount = bind(Obligation<T>::amount)
    val paid = bind(Obligation<T>::role)
    val counterparty = bind(Obligation<T>::counterparty)
    val status = bind(Obligation<T>::status)
    val settlementInstructions = bind(Obligation<T>::settlementInstructions)
}

fun <T : Any> ObligationContract.State<T>.toUiModel(cordaRpcOps: CordaRPCOps): Obligation<T> {
    // Resolve identities.
    val resolver = { abstractParty: AbstractParty -> cordaRpcOps.wellKnownPartyFromAnonymous(abstractParty)!! }
    val wellKnown = withWellKnownIdentities(resolver)
    val us = cordaRpcOps.nodeInfo().legalIdentities.first()

    // Determine counterparty and who is obligor.
    val counterparty = if (wellKnown.obligor == us) wellKnown.obligee else wellKnown.obligor
    val isPayer = wellKnown.obligor == us


    return Obligation<T>(linearId, faceAmount, counterparty as Party, isPayer, settlementInstructions)
}