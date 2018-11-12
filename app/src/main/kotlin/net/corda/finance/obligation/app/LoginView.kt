package net.corda.finance.obligation.app

import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import tornadofx.*

class LoginView : View("Login") {
    private val model = object : ViewModel() {
        val host = bind { SimpleStringProperty() }
        val port = bind { SimpleIntegerProperty() }
        val username = bind { SimpleStringProperty() }
        val password = bind { SimpleStringProperty() }
        val configFile = bind { SimpleStringProperty() }
    }

    override val root = form {
        style { fontSize = 10.px }
        fieldset {
            field("Host and port") {
                textfield(model.host) {
                    required()
                    text = "localhost"
                    maxWidth = 70.0
                }
                textfield(model.port) {
                    required()
                    whenDocked { requestFocus() }
                    text = ""
                    maxWidth = 50.0
                }
            }
            field("User name") {
                textfield(model.username) {
                    required()
                    text = "user1"
                }
            }
            field("Password") {
                passwordfield(model.password) {
                    required()
                    text = "test"
                }
            }
            field("Ripple Config File") {
                textfield(model.configFile) {
                    required()
                    text = "ripple.conf"
                }
            }
        }

        button("Submit") {
            isDefaultButton = true
            action {
                cordaRpcOps = connectToCordaRpc(
                        hostAndPort = model.host.value + ":" + model.port.value,
                        username = model.username.value,
                        password = model.password.value
                )
                find(LoginView::class).close()
                println(model.configFile.value.toString())
                find<ObligationView>(mapOf(ObligationView::configFile to model.configFile.value.toString())).openWindow()
            }
        }
    }
}