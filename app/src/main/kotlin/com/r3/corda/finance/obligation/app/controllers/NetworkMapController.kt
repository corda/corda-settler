package com.r3.corda.finance.obligation.app.controllers

import javafx.beans.property.SimpleObjectProperty
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import net.corda.client.jfx.utils.observeOnFXThread
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.NetworkMapCache
import tornadofx.*


class NetworkMapController : Controller() {

    private val cordaRpcOps: CordaRPCOps by param()
    private val networkMapFeed = cordaRpcOps.networkMapFeed()

    val us: SimpleObjectProperty<Party> = SimpleObjectProperty(cordaRpcOps.nodeInfo().legalIdentities.first())

    val allParties: ObservableList<NodeInfo> = FXCollections.observableArrayList(networkMapFeed.snapshot).apply {
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

}