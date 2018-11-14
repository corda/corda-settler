package com.r3.corda.finance.obligation.app.views

import com.r3.corda.finance.obligation.app.models.UserModel
import javafx.geometry.Pos
import net.corda.core.messaging.CordaRPCOps
import tornadofx.*

class MainView : View("Corda Settler") {
    val cordaRpcOps: CordaRPCOps by param()
    val user: UserModel by inject()

    override val root = vbox(10) {
        setPrefSize(800.0, 600.0)
        alignment = Pos.CENTER
        label(user.username)
        label(user.hostAndPort)
    }
}

