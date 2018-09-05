package net.corda.finance.obligation.app

import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import net.corda.client.jfx.utils.map
import net.corda.client.jfx.utils.observeOnFXThread
import net.corda.core.contracts.Amount
import net.corda.core.identity.Party
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.NetworkMapCache
import net.corda.finance.obligation.client.flows.CreateObligation
import net.corda.finance.obligation.types.DigitalCurrency
import tornadofx.*
import java.util.*

class AddObligationView : Fragment("Add obligation") {
    private val networkMapFeed = cordaRpcOps!!.networkMapFeed()
    private val parties: ObservableList<NodeInfo> = FXCollections.observableArrayList(networkMapFeed.snapshot)

    private val model = object : ViewModel() {
        val amount = bind { SimpleStringProperty() }
        val currency = bind { SimpleStringProperty() }
        val role = bind { SimpleStringProperty() }
        val counterparty = bind { SimpleObjectProperty<Party>() }
        val anonymous = bind { SimpleBooleanProperty() }
    }

    init {
        networkMapFeed.updates.observeOnFXThread().subscribe { update ->
            parties.removeIf {
                when (update) {
                    is NetworkMapCache.MapChange.Removed -> it == update.node
                    is NetworkMapCache.MapChange.Modified -> it == update.previousNode
                    else -> false
                }
            }
            if (update is NetworkMapCache.MapChange.Modified || update is NetworkMapCache.MapChange.Added) {
                parties.addAll(update.node)
            }
        }
    }

    override val root = form {
        style { fontSize = 10.px }
        fieldset {
            field("Amount") {
                textfield(model.amount) {
                    required()
                    whenDocked { requestFocus() }
                }
            }
            field("Currency") {
                combobox<String>(model.currency) {
                    items = FXCollections.observableArrayList("GBP", "XRP", "USD")
                }
            }
            field("Role") {
                combobox<String>(model.role) {
                    items = FXCollections.observableArrayList("Obligor", "Obligee")
                }
            }
            field("Counterparty") {
                choicebox<Party>(model.counterparty) {
                    items = parties.map { it.singleIdentityAndCert().party }
                    converter = stringConverter { it?.let { PartyNameFormatter.short.format(it.name) } ?: "" }
                }
            }
            field("Anonymous") {
                checkbox("", model.anonymous)
            }
        }

        button("Submit") {
            isDefaultButton = true

            action {
                model.commit {
                    val currency = model.currency.value
                    val amount = when (currency) {
                        "XRP" -> {
                            val rippleAmount = model.amount.value.toLong() * 1000000
                            Amount(rippleAmount, DigitalCurrency.getInstance(currency))
                        }
                        else -> {
                            Amount(model.amount.value.toLong(), Currency.getInstance(currency))
                        }
                    }

                    val role = CreateObligation.InitiatorRole.valueOf(model.role.value.toUpperCase())
                    val counterparty = model.counterparty.value
                    val anonymous = model.anonymous.value

                    cordaRpcOps!!.startFlowDynamic(CreateObligation.Initiator::class.java, amount, role, counterparty, anonymous)
                    scene.window.hide()
                }
            }
        }
    }
}