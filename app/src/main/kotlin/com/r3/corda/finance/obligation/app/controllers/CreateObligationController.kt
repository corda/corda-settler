package com.r3.corda.finance.obligation.app.controllers

import com.r3.corda.finance.obligation.client.flows.CreateObligation
import com.r3.corda.finance.obligation.types.DigitalCurrency
import com.r3.corda.finance.obligation.types.FiatCurrency
import com.r3.corda.finance.obligation.types.Money
import javafx.beans.property.SimpleStringProperty
import net.corda.core.contracts.Amount
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.getOrThrow
import tornadofx.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class CreateObligationController : Controller() {

    private val cordaRpcOps: CordaRPCOps by param()

    val statusProperty = SimpleStringProperty()
    var status by statusProperty

    private fun parseToken(tokenType: String): Money? {
        return try {
            DigitalCurrency.getInstance(tokenType)
        } catch (e: IllegalArgumentException) {
            try {
                FiatCurrency.getInstance(tokenType)
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }

    fun createObligation(
            quantity: Number,
            currency: String,
            role: String,
            dueBy: LocalDate,
            counterparty: Party,
            anonymous: Boolean
    ) {
        runLater { status = "" }

        val tokenType = parseToken(currency)
        if (tokenType == null) {
            runLater { status = "$currency not supported." }
            return
        }

        val amount = Amount.fromDecimal(BigDecimal.valueOf(quantity.toDouble()), tokenType)

        val flowResult = try {
            cordaRpcOps.startFlowDynamic(
                    CreateObligation.Initiator::class.java,
                    amount,
                    CreateObligation.InitiatorRole.valueOf(role.toUpperCase()),
                    counterparty,
                    dueBy.atStartOfDay(ZoneOffset.UTC).toInstant(),
                    anonymous
            ).returnValue.getOrThrow()
        } catch(e: Exception) {
            // TODO: Handle exception types.
            runLater { status = e.toString() }
            return
        }
        runLater {
            val truncatedHash = flowResult.tx.id.toString().substring(0, 20)
            status = "Obligation created with hash\n $truncatedHash..."
        }
    }

}