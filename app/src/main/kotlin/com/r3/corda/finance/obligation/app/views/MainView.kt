package com.r3.corda.finance.obligation.app.views

import com.r3.corda.finance.obligation.app.controllers.NetworkMapController
import com.r3.corda.finance.obligation.app.controllers.ObligationsController
import com.r3.corda.finance.obligation.app.formatAmount
import com.r3.corda.finance.obligation.app.formatCounterparty
import com.r3.corda.finance.obligation.app.formatSettlementMethod
import com.r3.corda.finance.obligation.app.models.ObligationModel
import com.r3.corda.finance.obligation.app.models.UiObligation
import com.r3.corda.finance.obligation.app.models.User
import com.r3.corda.finance.obligation.app.models.UserModel
import com.r3.corda.finance.obligation.app.stringConverter
import com.r3.corda.finance.obligation.states.Obligation
import com.r3.corda.finance.obligation.types.Payment
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.value.ObservableBooleanValue
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.geometry.VPos
import javafx.scene.control.TableColumn
import javafx.scene.text.FontWeight
import javafx.stage.StageStyle
import net.corda.core.contracts.Amount
import net.corda.core.messaging.CordaRPCOps
import tornadofx.*

class MainView : View("Corda Settler") {
    val cordaRpcOps: CordaRPCOps by param()
    private val user: UserModel by inject()
    private val obligation: ObligationModel by inject()

    private val selectedObligationProperty = SimpleBooleanProperty(false)
    private var selectedObligation by selectedObligationProperty

    private val obligationsController: ObligationsController by inject(params = mapOf("cordaRpcOps" to cordaRpcOps))
    private val networkMapController: NetworkMapController by inject(params = mapOf("cordaRpcOps" to cordaRpcOps))

    override val root = vbox {
        menubar {
            menu("File") {
                item("Create obligation").action {
                    val params = mapOf(CreateObligationView::cordaRpcOps to cordaRpcOps)
                    find<CreateObligationView>(params).openWindow(stageStyle = StageStyle.UTILITY)
                }
                item("Preferences").action {
                    val params = mapOf(PreferencesView::cordaRpcOps to cordaRpcOps)
                    find<PreferencesView>(params).openWindow(stageStyle = StageStyle.UTILITY)
                }
                item("Exit")
            }
        }
        hbox {
            minHeight = 800.0
            borderpane {
                center {
                    tableview(obligationsController.obligations) {
                        minWidth = 500.0
                        columnResizePolicy = SmartResize.POLICY
                        readonlyColumn("Due By", UiObligation::dueBy)
                        readonlyColumn("Amount", UiObligation::amount).cellFormat { text = formatAmount(it) }
                        column("Counterparty") { it: TableColumn.CellDataFeatures<UiObligation, String> ->
                            SimpleStringProperty(formatCounterparty(it.value, networkMapController.us.value))
                        }
                        column("Status") { it: TableColumn.CellDataFeatures<UiObligation, String> ->
                            SimpleStringProperty(it.value.settlementStatus.toString())
                        }
                        bindSelected(obligation)
                        onUserSelect(1) { selectedObligation = true }
                    }
                }
                bottom {
                    hbox {
                        style { padding = box(10.px) }
                        label("Logged into ${user.hostAndPort.value} as ${user.username.value}")
                    }
                }
            }
            borderpane {
                minWidth = 500.0
                center {
                    stackpane {
                        vbox {
                            alignment = Pos.CENTER
                            hiddenWhen { selectedObligationProperty }
                            label("Select an obligation from the table.")
                        }
                        vbox {
                            padding = Insets(10.0, 10.0, 10.0, 10.0)
                            hiddenWhen { selectedObligationProperty.not() }
                            label("Obligation Details").style {
                                paddingBottom = 10
                                fontSize = 16.px
                            }
                            gridpane {
                                padding = Insets(10.0, 10.0, 10.0, 10.0)
                                row {

                                    label("Linear ID:") { padding = Insets(10.0, 10.0, 10.0, 10.0) }
                                    label(obligation.linearId)
                                }
                                row {
                                    label("Amount:") { padding = Insets(10.0, 10.0, 10.0, 10.0) }
                                    label(obligation.amount, converter = stringConverter { formatAmount(it) })
                                    button("Change currency") {
                                        style { fontSize = 8.px }
                                    }
                                }
                                row {
                                    label("Obligor:") { padding = Insets(10.0, 10.0, 10.0, 10.0) }
                                    label(obligation.obligor, converter = stringConverter {
                                        it?.nameOrNull()?.organisation ?: ""
                                    })
                                }
                                row {
                                    label("Obligee:") { padding = Insets(10.0, 10.0, 10.0, 10.0) }
                                    label(obligation.obligee, converter = stringConverter {
                                        it?.nameOrNull()?.organisation ?: ""
                                    })
                                }
                                row {
                                    label("Created at:") { padding = Insets(10.0, 10.0, 10.0, 10.0) }
                                    label(obligation.createdAt)
                                }
                                row {
                                    label("Due by:") { padding = Insets(10.0, 10.0, 10.0, 10.0) }
                                    label(obligation.dueBy)
                                    button("Change due date") {
                                        style { fontSize = 8.px }
                                    }
                                }
                                row {
                                    label("Settlement status:") { padding = Insets(10.0, 10.0, 10.0, 10.0) }
                                    label(obligation.settlementStatus)
                                }
                                row {
                                    label("Settlement method:") { padding = Insets(10.0, 10.0, 10.0, 10.0) }
                                    label(obligation.settlementMethod, converter = stringConverter { formatSettlementMethod(it) })
                                    button("Add settlement instructions") {
                                        visibleWhen { SimpleBooleanProperty(!obligation.hasSettlementMethod.value) }
                                        style { fontSize = 8.px }
                                    }
                                }
                            }
                            label("Payments").style {
                                paddingBottom = 10
                                fontSize = 16.px
                            }
                            vbox {
                                padding = Insets(10.0, 10.0, 10.0, 10.0)
                                tableview(obligation.payments) {
                                    readonlyColumn("Amount", Payment<*>::amount)
                                    readonlyColumn("Reference", Payment<*>::paymentReference)
                                    readonlyColumn("Status", Payment<*>::status)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

