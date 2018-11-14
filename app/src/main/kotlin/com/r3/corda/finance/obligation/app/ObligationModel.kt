package com.r3.corda.finance.obligation.app

import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import tornadofx.*

class ObligationModel<T : Any>(linearId: UniqueIdentifier, amount: Amount<T>, counterparty: Party, payer: Boolean, settlementInstructions: SettlementInstructions?) {
    val linearId by property(linearId)
    val amount by property(amount)
    val counterparty by property(counterparty)
    val isPayer by property(payer)
    val hasSettlementInstructions by property(settlementInstructions != null)
    val settlementInstructions by property(settlementInstructions)
}

// TODO: There is a bug in this function. Fix it!
fun Obligation.State<*>.toUiModel(): ObligationModel<*> {
    // We should always be able to resolve parties for our own obligations.
    val resolver = { abstractParty: AbstractParty -> abstractParty.toKnownParty()!! }
    val wellKnown = this.withWellKnownIdentities(resolver)
    val us = cordaRpcOps!!.nodeInfo().legalIdentities.first()
    val counterparty = if (wellKnown.obligor == us) wellKnown.obligee else wellKnown.obligor
    val isPayer = wellKnown.obligor == us
    return ObligationModel(linearId, faceAmount, counterparty as Party, isPayer, settlementInstructions)
}