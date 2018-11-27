package com.r3.corda.finance.obligation.app.views

import javafx.scene.Parent
import net.corda.core.messaging.CordaRPCOps
import tornadofx.*

class PreferencesView : View("Preferences") {

    val cordaRpcOps: CordaRPCOps by param()

    override val root: Parent = vbox {

    }

}