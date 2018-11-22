package com.r3.corda.finance.obligation.app.models

class SettlementInstructions() {

}

class SettlementInstructionsModel() {

}

enum class Role { OBLIGOR, OBLIGEE }

/**
 * We are ignoring the "paid" property since obligations are either outstanding or fully paid. We are adding a "role"
 * property to indicate which party is the obligor/obligee.
 */
//class ObligationModel(
//        linearId: UniqueIdentifier,
//        faceAmount: Amount<*>,
//        counterparty: Party,
//        role: Role,
//        settlementStatus: SettlementStatus,
//        settlementMethod: SettlementInstructions?
//) {
//
//    val linearIdProperty = SimpleStringProperty()
//    var linearId by linearIdProperty
//
//    val amountProperty = SimpleObjectProperty<Amount<*>>()
//    var amount by amountProperty
//
//    val roleProperty = SimpleObjectProperty<Role>()
//    var role by roleProperty
//
//    val counterpartyProperty = SimpleStringProperty()
//    var counterparty by counterpartyProperty
//
//    val statusProperty = SimpleStringProperty()
//    var settlementStatus by statusProperty
//
//    val settlementInstructionsProperty = SimpleObjectProperty<SettlementInstructions>()
//    var settlementMethod by settlementInstructionsProperty
//}

//class ObligationModel : ItemViewModel<ObligationContract>() {
//    val linearId = bind(ObligationContract::linearId)
//    val amount = bind(ObligationContract::amount)
//    val paid = bind(ObligationContract::role)
//    val counterparty = bind(ObligationContract::counterparty)
//    val settlementStatus = bind(ObligationContract::settlementStatus)
//    val settlementMethod = bind(ObligationContract::settlementMethod)
//}
//
//fun ObligationContract.State<*>.toModel(cordaRpcOps: CordaRPCOps): ObligationContract {
//    // Resolve identities.
//    val resolver = { abstractParty: AbstractParty -> cordaRpcOps.wellKnownPartyFromAnonymous(abstractParty)!! }
//    val wellKnown = withWellKnownIdentities(resolver)
//    val us = cordaRpcOps.nodeInfo().legalIdentities.first()
//
//    // Determine counterparty and who is obligor.
//    val counterparty = if (wellKnown.obligor == us) wellKnown.obligee else wellKnown.obligor
//    val isPayer = if (wellKnown.obligor == us) Role.OBLIGOR else Role.OBLIGEE
//
//    // Return an obligation model.
//    return ObligationContract(linearId, faceAmount, counterparty as Party, isPayer, settlementStatus, settlementMethod)
//}