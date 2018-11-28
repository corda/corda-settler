package com.r3.corda.finance.obligation.app.controllers

import com.r3.corda.finance.obligation.app.connectToCordaRpc
import com.r3.corda.finance.obligation.app.models.User
import com.r3.corda.finance.obligation.app.models.UserModel
import com.r3.corda.finance.obligation.app.views.LoginView
import com.r3.corda.finance.obligation.app.views.MainView
import javafx.beans.property.SimpleStringProperty
import net.corda.client.rpc.RPCException
import org.apache.activemq.artemis.api.core.ActiveMQConnectionTimedOutException
import org.apache.activemq.artemis.api.core.ActiveMQSecurityException
import tornadofx.*

class LoginController : Controller() {

    val statusProperty = SimpleStringProperty()
    var status by statusProperty
    val userModel: UserModel by inject()

    fun login(host: String, port: Number, user: String, pass: String) {
        runLater { status = "" }
        val hostAndPort = "$host:$port"

        // Login to the node and handle errors.
        val cordaRpcOps = try {
            connectToCordaRpc(hostAndPort, user, pass)
        } catch (e: RPCException) {
            runLater { status = "No node available at $hostAndPort." }
            return
        } catch (e: ActiveMQSecurityException) {
            runLater { status = "Username or password incorrect." }
            return
        } catch (e: ActiveMQConnectionTimedOutException) {
            runLater { status = "Connection attempt timed out." }
            return
        } catch (e: IllegalArgumentException) {
            runLater { status = e.message }
            return
        }

        runLater {
            userModel.item = User(user, hostAndPort)
            find(LoginView::class).close()
            val params = mapOf(MainView::cordaRpcOps to cordaRpcOps)
            find<MainView>(params).openWindow()
        }
    }

}