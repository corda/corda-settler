package com.r3.corda.finance.obligation.app.controllers

import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import net.corda.client.jfx.utils.filter
import net.corda.client.jfx.utils.filterNotNull
import net.corda.client.jfx.utils.map
import net.corda.client.jfx.utils.observeOnFXThread
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.NetworkMapCache
import tornadofx.*


class NetworkMapController : Controller() {

    private val cordaRpcOps: CordaRPCOps by param()
    private val networkMapFeed = cordaRpcOps.networkMapFeed()

    /** Returns ALL node infos and is updated in real-time. */
    val allNodeInfos: ObservableList<NodeInfo> = FXCollections.observableArrayList(networkMapFeed.snapshot).apply {
        networkMapFeed.updates.observeOnFXThread().subscribe { update ->
            removeIf {
                when (update) {
                    is NetworkMapCache.MapChange.Removed -> it == update.node
                    is NetworkMapCache.MapChange.Modified -> it == update.previousNode
                    else -> false
                }
            }
            if (update is NetworkMapCache.MapChange.Modified || update is NetworkMapCache.MapChange.Added) {
                addAll(update.node)
            }
        }
    }

    /** Our NodeInfo. */
    val ourNodeinfo: SimpleObjectProperty<NodeInfo> = SimpleObjectProperty(cordaRpcOps.nodeInfo())

    /** NodeInfos for all notaries. Assumption is that notaries do not change after this app is started. */
    val notaryNodeInfos: ObservableList<NodeInfo> = cordaRpcOps.notaryIdentities().mapNotNull { cordaRpcOps.nodeInfoFromParty(it) }.observable()

    /** Our party object. */
    val us: ObservableValue<Party> = SimpleObjectProperty(cordaRpcOps.nodeInfo().legalIdentities.first())

    /** All parties. */
    val allParties: ObservableList<Party> = allNodeInfos.map { it.legalIdentities.first() }.observable()

    /** All parties. */
    val notaryParties: ObservableList<Party> = notaryNodeInfos.map { it.legalIdentities.first() }.observable()

    /** All parties less notaries and ourselves. */
    val allCounterparties: ObservableList<Party> get() = allParties.filtered { party ->
        party != us.value && party !in notaryParties
    }

}