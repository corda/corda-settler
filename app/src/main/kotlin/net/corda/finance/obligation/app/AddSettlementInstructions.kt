package net.corda.finance.obligation.app

import com.ripple.core.coretypes.AccountID
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import net.corda.client.jfx.utils.map
import net.corda.client.jfx.utils.observeOnFXThread
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.NetworkMapCache
import net.corda.finance.obligation.client.flows.AddSettlementInstructions
import net.corda.finance.obligation.types.OnLedgerSettlementTerms
import net.corda.finance.ripple.types.RippleSettlementInstructions
import tornadofx.*

class AddSettlementInstructions : Fragment("Add settlement instructions") {

    val linearId: SimpleObjectProperty<UniqueIdentifier> = SimpleObjectProperty()

    private val networkMapFeed = cordaRpcOps!!.networkMapFeed()
    private val parties: ObservableList<NodeInfo> = FXCollections.observableArrayList(networkMapFeed.snapshot)

    private val model = object : ViewModel() {
        val address = bind { SimpleStringProperty() }
        val oracle = bind { SimpleObjectProperty<Party>() }
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

    override val root = tabpane {
        //        minWidth = 400.00
//        minHeight = 300.00
        style { fontSize = 10.px }
        tab("Ripple") {
            form {
                fieldset {
                    field("XRP address") {
                        textfield(model.address) {
                            required()
                        }
                    }
                    field("Oracle") {
                        choicebox<Party>(model.oracle) {
                            items = parties.map { it.singleIdentityAndCert().party }
                            converter = stringConverter { it?.let { PartyNameFormatter.short.format(it.name) } ?: "" }
                        }
                    }
                }
                button("Submit") {
                    action {
                        val account = AccountID.fromString(model.address.value)
                        val settlementInstructions = RippleSettlementInstructions(account, model.oracle.value)
                        model.commit {
                            cordaRpcOps!!.startFlowDynamic(AddSettlementInstructions::class.java, linearId.get(), settlementInstructions)
                        }
                    }
                }
            }
        }
        tab("On-ledger") {
            form {
                label {
                    text = "If settling on ledger, there's no need to do anything here."
                }
                button("Submit").action {
                    cordaRpcOps!!.startFlowDynamic(AddSettlementInstructions::class.java, linearId.get(), OnLedgerSettlementTerms())
                }
            }
        }
    }
}