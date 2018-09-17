package net.corda.finance.obligation.app

import javafx.collections.FXCollections
import javafx.geometry.Insets
import javafx.stage.StageStyle
import net.corda.client.jfx.utils.map
import net.corda.client.jfx.utils.observeOnFXThread
import net.corda.core.messaging.vaultTrackBy
import net.corda.finance.obligation.contracts.Obligation
import net.corda.finance.obligation.types.DigitalCurrency
import net.corda.finance.ripple.RippleClientForPayment
import tornadofx.*
import java.util.*

class ObligationView : Fragment("Obligation app") {

    private val obligationsFeed = cordaRpcOps!!.vaultTrackBy<Obligation.State<*>>()
    private val obligations = FXCollections.observableArrayList(obligationsFeed.snapshot.states)

    init {
        obligationsFeed.updates.observeOnFXThread().subscribe {
            obligations.removeAll(it.consumed)
            obligations.addAll(it.produced)
        }
    }

    override val root = borderpane {
        top {
            label {
                // TODO: Update balance.
                padding = Insets(10.0)
                val client = RippleClientForPayment("ripple.conf")
                val accountInfo = client.accountInfo(client.address)
                val balance = accountInfo.accountData.balance
                text = "Ripple account balance: $balance"
            }
        }
        minWidth = 800.0
        style { fontSize = 10.px }
        center {
            tableview<ObligationModel<*>> {
                items = obligations.map { it.state.data }.transform { it.toUiModel() }
                columnResizePolicy = SmartResize.POLICY
                readonlyColumn("Linear ID", ObligationModel<*>::linearId)
                readonlyColumn("Currency", ObligationModel<*>::amount).cellFormat {
                    text = (it.token as? DigitalCurrency)?.currencyCode ?: (it.token as Currency).currencyCode
                }
                readonlyColumn("Amount", ObligationModel<*>::amount).cellFormat {
                    text = it.quantity.toString()
                }
                readonlyColumn("Counterparty", ObligationModel<*>::counterparty).cellFormat {
                    text = PartyNameFormatter.short.format(it.name)
                }
                readonlyColumn("Actions", ObligationModel<*>::hasSettlementInstructions).cellFormat { settlementInstructions ->
                    if (!settlementInstructions) {
                        graphic = hbox {
                            style { fontSize = 8.px }
                            button("Add settlement instructions").action {
                                find<AddSettlementInstructions>().apply {
                                    linearId.set(focusModel.focusedItem.linearId)
                                    openModal(stageStyle = StageStyle.UTILITY)
                                }
                            }
                        }
                    } else {
                        graphic = hbox {
                            style { fontSize = 8.px }
                            button("Make payment").action { }
                        }
                    }
                }
            }
        }
        bottom {
            toolbar {
                button("Add obligation").action {
                    find<AddObligationView>().openModal(stageStyle = StageStyle.UTILITY)
                }
            }
        }
    }
}