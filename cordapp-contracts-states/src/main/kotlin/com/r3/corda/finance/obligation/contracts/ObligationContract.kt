package com.r3.corda.finance.obligation.contracts

import com.r3.corda.finance.obligation.commands.ObligationCommands
import com.r3.corda.finance.obligation.singleInput
import com.r3.corda.finance.obligation.singleOutput
import com.r3.corda.finance.obligation.states.Obligation
import com.r3.corda.finance.obligation.types.Money
import com.r3.corda.finance.obligation.types.PaymentStatus
import net.corda.core.contracts.*
import net.corda.core.transactions.LedgerTransaction
import java.math.BigDecimal
import java.time.Instant
import kotlin.reflect.KProperty1

/**
 * Contract code for the [Obligation].
 *
 * This code has been thrown together reasonably quickly and doesn't yet have any unit tests. Don't copy any of it
 * for your own contracts. The digital assets team at R3 will be putting together a production grade obligation contract
 * and flow library within the next few months (as at December 2018).
 */
class ObligationContract : Contract {

    companion object {
        @JvmStatic
        val CONTRACT_REF: ContractClassName = "com.r3.corda.finance.obligation.contracts.ObligationContract"
    }

    override fun verify(tx: LedgerTransaction) {
        // Obligation transactions can only contain one command.
        val command = tx.commands.requireSingleCommand<ObligationCommands>()
        when (command.value) {
            is ObligationCommands.Create -> handleCreate(tx)
            is ObligationCommands.Novate.UpdateFaceAmountQuantity -> handleNovateQuantity(tx)
            is ObligationCommands.Novate.UpdateFaceAmountToken<*, *> -> handleNovateToken(tx)
            is ObligationCommands.Novate.UpdateParty -> handleNovateParty(tx)
            is ObligationCommands.Novate.UpdateDueBy -> handleNovateDueBy(tx)
            is ObligationCommands.UpdateSettlementMethod -> handleUpdateSettlementMethod(tx)
            is ObligationCommands.Cancel -> handleCancel(tx)
            is ObligationCommands.AddPayment -> handleAddPayment(tx)
            is ObligationCommands.UpdatePayment -> handleUpdatePayment(tx)
        }
    }

    private fun checkPropertyInvariants(input: Obligation<*>, output: Obligation<*>, properties: Set<KProperty1<Obligation<*>, Any?>>): Boolean {
        return properties.all { property -> property.get(input) == property.get(output) }
    }

    /** CREATION. */

    private fun handleCreate(tx: LedgerTransaction) {
        require(tx.outputs.size == 1) { "Create obligation transactions may only contain one output." }
        require(tx.inputs.isEmpty()) { "Create obligation transactions must not contain any inputs." }
        val obligation = tx.singleOutput<Obligation<Money>>()
        obligation.apply {
            require(faceAmount > Amount.zero(faceAmount.token)) { "Obligations must not be created with a zero face amount." }
            require(obligor != obligee) { "Obligations cannot be between the same legal identity." }
            require(participants.size == 2) { "Obligations can only contain two participants." }
            if (dueBy != null) {
                require(dueBy > Instant.now()) { "The due date must be in the future." }
            }
            require(payments.isEmpty()) { "Obligations cannot be created with any initial payments." }
            require(!inDefault) { "Obligations cannot be in default when they are created." }
            val command = tx.commands.requireSingleCommand<ObligationCommands.Create>()
            require(command.signers.toSet() == obligation.participants.map { it.owningKey}.toSet()) {
                "Both the obligor and obligee must sign the transaction to create an obligation."
            }
        }
    }

    /** CANCELLATION. */

    // Assumption is that this only happens if both parties agree to it. however, currently as there is no HCI, if one
    // party proposes a cancellation then the other will just automatically sign the proposal, so be careful with this!
    private fun handleCancel(tx: LedgerTransaction) {
        require(tx.inputs.size == 1) { "Cancel obligation transactions may only contain one input." }
        require(tx.outputs.isEmpty()) { "Cancel obligation transactions must not contain outputs." }
        val obligation = tx.singleInput<Obligation<Money>>()
        val command = tx.commands.requireSingleCommand<ObligationCommands.Cancel>()
        require(command.signers.toSet() == obligation.participants.map { it.owningKey }.toSet()) {
            "Both the obligor and obligee must sign the transaction to cancel an obligation."
        }
    }

    /** NOVATION. */

    private fun handleNovateAmount(input: Obligation<*>, output: Obligation<*>) {
        // Stuff that shouldn't change.
        val invariantProperties = setOf(
                Obligation<*>::linearId,
                Obligation<*>::obligor,
                Obligation<*>::obligee,
                Obligation<*>::settlementMethod,
                Obligation<*>::dueBy,
                Obligation<*>::createdAt,
                Obligation<*>::payments
        )
        checkPropertyInvariants(input, output, invariantProperties)
    }

    private fun handleNovateQuantity(tx: LedgerTransaction) {
        val input = tx.singleInput<Obligation<*>>()
        val output = tx.singleOutput<Obligation<*>>()
        val command = tx.commands.requireSingleCommand<ObligationCommands.Novate.UpdateFaceAmountQuantity>()
        handleNovateAmount(input, output)
        val inputFaceAmount = input.faceAmount
        val outputFaceAmount = output.faceAmount
        require(inputFaceAmount.quantity != outputFaceAmount.quantity) { "The face amount quantity must change." }
        require(inputFaceAmount.token == outputFaceAmount.token) {
            "The face amount token may not change when novating the face amount quantity."
        }
        require(output.faceAmount.quantity == command.value.newAmount.quantity) {
            "The output obligation quantity must be updated by the specified amount."
        }
        require(command.signers.toSet() == output.participants.map { it.owningKey}.toSet()) {
            "Both the obligor and obligee must sign the transaction to update the face amount quantity."
        }
    }

    private fun handleNovateToken(tx: LedgerTransaction) {
        val input = tx.singleInput<Obligation<*>>()
        val output = tx.singleOutput<Obligation<*>>()
        val command = tx.commands.requireSingleCommand<ObligationCommands.Novate.UpdateFaceAmountToken<*, *>>()
        handleNovateAmount(input, output)
        val inputFaceAmount = input.faceAmount
        val outputFaceAmount = output.faceAmount
        require(inputFaceAmount.token != outputFaceAmount.token) { "The face amount token must change." }
        require(command.value.fxRate != null) { "There must be an exchange rate in the novate command." }
        val newQuantity = inputFaceAmount.toDecimal() * BigDecimal.valueOf(command.value.fxRate!!.toDouble())
        val newAmount = Amount.fromDecimal(newQuantity, command.value.newToken)
        require(newAmount == outputFaceAmount) { "The token or quantity was updated correctly." }
        require(command.signers.toSet() == output.participants.map { it.owningKey}.toSet() + command.value.oracle.owningKey) {
            "Both the obligor, obligee and specified oracle must sign the transaction to update the face amount token."
        }
    }

    private fun handleNovateParty(tx: LedgerTransaction) {
        val input = tx.singleInput<Obligation<*>>()
        val output = tx.singleOutput<Obligation<*>>()
        val command = tx.commands.requireSingleCommand<ObligationCommands.Novate.UpdateParty>()
        // Stuff that explicitly shouldn't change.
        val invariantProperties = setOf(
                Obligation<*>::faceAmount,
                Obligation<*>::linearId,
                Obligation<*>::dueBy,
                Obligation<*>::createdAt,
                Obligation<*>::payments,
                Obligation<*>::settlementMethod
        )
        checkPropertyInvariants(input, output, invariantProperties)
        if (input.obligor == command.value.oldParty) {
            require(output.obligor == command.value.newParty) { "New party not updated correctly." }
            checkPropertyInvariants(input, output, setOf(Obligation<*>::obligee))
        } else {
            require(output.obligee == command.value.newParty) { "New party not updated correctly." }
            checkPropertyInvariants(input, output, setOf(Obligation<*>::obligor))
        }
        // Need all three parties to sign.
        val allParties = output.participants.map { it.owningKey } + input.participants.map { it.owningKey }
        require(command.signers.toSet() == allParties.toSet()) {
            "Both the old and new parties must sign an update obligation party transaction."
        }
    }

    private fun handleNovateDueBy(tx: LedgerTransaction) {
        val input = tx.singleInput<Obligation<*>>()
        val output = tx.singleOutput<Obligation<*>>()
        val command = tx.commands.requireSingleCommand<ObligationCommands.Novate.UpdateDueBy>()
        // Stuff that explicitly shouldn't change.
        val invariantProperties = setOf(
                Obligation<*>::faceAmount,
                Obligation<*>::linearId,
                Obligation<*>::obligor,
                Obligation<*>::obligee,
                Obligation<*>::createdAt,
                Obligation<*>::payments,
                Obligation<*>::settlementMethod
        )
        checkPropertyInvariants(input, output, invariantProperties)
        require(input.dueBy != output.dueBy) { "The due by date must change." }
        if (output.dueBy != null) {
            require(output.dueBy > Instant.now()) { "The due by date cannot." }
        }
        require(command.signers.toSet() == output.participants.map { it.owningKey}.toSet()) {
            "Both the obligor and obligee must sign the transaction to update the due date."
        }
    }

    /** SETTLEMENT. */

    private fun handleUpdateSettlementMethod(tx: LedgerTransaction) {
        val input = tx.singleInput<Obligation<*>>()
        val output = tx.singleOutput<Obligation<*>>()
        // Stuff that explicitly shouldn't change.
        val invariantProperties = setOf(
                Obligation<*>::faceAmount,
                Obligation<*>::linearId,
                Obligation<*>::obligor,
                Obligation<*>::obligee,
                Obligation<*>::dueBy,
                Obligation<*>::createdAt,
                Obligation<*>::payments
        )
        checkPropertyInvariants(input, output, invariantProperties)
        require(output.settlementMethod != null) {
            "You must provide a settlement method when updating settlement method."
        }
        require(input.payments.isEmpty()) {
            "You cannot change settlement method if payments have already been made in respect of an already added " +
                    "settlement method. "
        }
    }

    private fun handleAddPayment(tx: LedgerTransaction) {
        val input = tx.singleInput<Obligation<*>>()
        val output = tx.singleOutput<Obligation<*>>()
        val command = tx.commands.requireSingleCommand<ObligationCommands.AddPayment>()
        // Stuff that explicitly shouldn't change.
        val invariantProperties = setOf(
                Obligation<*>::faceAmount,
                Obligation<*>::linearId,
                Obligation<*>::obligor,
                Obligation<*>::obligee,
                Obligation<*>::dueBy,
                Obligation<*>::createdAt,
                Obligation<*>::settlementMethod
        )
        checkPropertyInvariants(input, output, invariantProperties)
        require(input.settlementMethod != null) {
            "There must be a settlement method specified before payments can be registered against this obligation."
        }
        require(input.payments.size + 1 == output.payments.size) { "You can only add one payment at once." }
        val payment = output.payments.single { it.paymentReference == command.value.ref }
        require(payment.status == PaymentStatus.SENT) { "Payments can only be added with a SENT status." }
    }

    private fun handleUpdatePayment(tx: LedgerTransaction) {
        val input = tx.singleInput<Obligation<*>>()
        val output = tx.singleOutput<Obligation<*>>()
        val command = tx.commands.requireSingleCommand<ObligationCommands.UpdatePayment>()
        // Stuff that explicitly shouldn't change.
        val invariantProperties = setOf(
                Obligation<*>::faceAmount,
                Obligation<*>::linearId,
                Obligation<*>::obligor,
                Obligation<*>::obligee,
                Obligation<*>::dueBy,
                Obligation<*>::createdAt,
                Obligation<*>::settlementMethod
        )
        checkPropertyInvariants(input, output, invariantProperties)
        val inputPayment = input.payments.single { it.paymentReference == command.value.ref }
        require(inputPayment.status == PaymentStatus.SENT) { "Only payments with a SENT status can be updated." }
    }

}