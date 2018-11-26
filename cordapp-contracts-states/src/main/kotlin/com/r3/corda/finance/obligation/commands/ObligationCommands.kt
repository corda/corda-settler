package com.r3.corda.finance.obligation.commands

import com.r3.corda.finance.obligation.types.Money
import net.corda.core.contracts.Amount
import net.corda.core.contracts.CommandData
import net.corda.core.contracts.TypeOnlyCommandData
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import java.time.Instant

/** All the things you can do with an obligation. */
interface ObligationCommands : CommandData {

    /** Create a new obligation. */
    class Create : ObligationCommands, TypeOnlyCommandData()

    /** Change the details of an obligation. */
    sealed class Novate : ObligationCommands {

        /** Change the face value quantity of the obligation. */
        class UpdateFaceAmountQuantity(val newAmount: Amount<Money>) : Novate()

        /** Change the face amount token of the obligation. This involves an fx conversion. */
        class UpdateFaceAmountToken<OLD : Money, NEW : Money>(
                val oldToken: OLD,
                val newToken: NEW,
                val oracle: Party,
                val fxRate: Number? = null
        ) : Novate()

        /** Change the due by date. */
        class UpdateDueBy(val newDueBy: Instant) : Novate()

        /** Change one of the parties. */
        class UpdateParty(val oldParty: AbstractParty, val newParty: AbstractParty) : Novate()
    }

    /** Add or update the settlement method. */
    class UpdateSettlementMethod : ObligationCommands, TypeOnlyCommandData()

    /** Record that a payment was made in respect of an obligation. */
    class AddPayment : ObligationCommands, TypeOnlyCommandData()

    /** Cancel the obligation - involves exiting the obligation state from the ledger. */
    class Cancel : ObligationCommands, TypeOnlyCommandData()
}