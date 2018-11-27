package com.r3.corda.finance.obligation.app.views

import com.r3.corda.finance.obligation.types.DigitalCurrency
import com.r3.corda.finance.obligation.app.PartyNameFormatter
import com.r3.corda.finance.obligation.app.controllers.CreateObligationController
import com.r3.corda.finance.obligation.app.controllers.NetworkMapController
import com.r3.corda.finance.obligation.app.stringConverter
import com.r3.corda.finance.obligation.client.flows.CreateObligation
import com.r3.corda.finance.obligation.types.FiatCurrency
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.scene.paint.Color
import javafx.scene.text.FontWeight
import net.corda.core.contracts.Amount
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import tornadofx.*
import java.time.LocalDate

class CreateObligationView : View("Add Obligation") {
    val cordaRpcOps: CordaRPCOps by param()

    private val networkMapController: NetworkMapController by inject(params = mapOf("cordaRpcOps" to cordaRpcOps))
    private val createObligationController: CreateObligationController by inject(params = mapOf("cordaRpcOps" to cordaRpcOps))

    private val model = ViewModel()
    private val quantity = model.bind { SimpleDoubleProperty() }
    private val currency = model.bind { SimpleStringProperty() }
    private val dueBy = model.bind { SimpleObjectProperty<LocalDate>() }
    private val role = model.bind { SimpleStringProperty() }
    private val counterparty = model.bind { SimpleObjectProperty<Party>() }
    private val anonymous = model.bind { SimpleBooleanProperty() }

    override val root = form {
        fieldset("Face amount") {
            field {
                combobox<String>(currency) {
                    // TODO: Get currencies from some service.
                    required()
                    items = FXCollections.observableArrayList("XRP", "USD")
                    maxWidth = 86.0
                }
                textfield(quantity) {
                    required()
                    filterInput { it.controlNewText.isDouble() }
                    maxWidth = 98.0
                }
            }
        }
        fieldset("Due by") {
            datepicker(dueBy) {
                value = LocalDate.now()
            }
        }
        fieldset {
            field("Role") {
                combobox<String>(role) {
                    items = FXCollections.observableArrayList("Obligor", "Obligee")
                    maxWidth = 100.0
                }
            }
        }
        fieldset {
            field("Counterparty") {
                combobox<Party>(counterparty) {
                    items = networkMapController.allCounterparties
                    converter = stringConverter { party ->
                        party?.let {
                            PartyNameFormatter.short.format(it.name)
                        } ?: "Unknown"
                    }
                    maxWidth = 100.0
                }
            }
        }
        fieldset {
            field("Anonymous") {
                checkbox("", anonymous)
            }
        }

        button("Create") {
            enableWhen(model.valid)
            isDefaultButton = true
            action {
                runAsyncWithProgress {
                    createObligationController.createObligation(
                            currency = currency.value,
                            quantity = quantity.value,
                            role = role.value,
                            dueBy = dueBy.value,
                            counterparty = counterparty.value,
                            anonymous = anonymous.value
                    )
                }
            }
        }
        label(createObligationController.statusProperty) {
            isWrapText = true
            style {
                fontWeight = FontWeight.BLACK
                textFill = Color.RED
                paddingTop = 10
                minHeight = 40.px
            }
        }
    }
}