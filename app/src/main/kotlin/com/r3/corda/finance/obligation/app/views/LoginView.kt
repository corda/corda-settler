package com.r3.corda.finance.obligation.app.views

import com.r3.corda.finance.obligation.app.controllers.LoginController
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import javafx.scene.paint.Color
import javafx.scene.text.FontWeight
import tornadofx.*

class LoginView : View("Login to the Corda Settler") {

    private val model = ViewModel()
    private val host = model.bind { SimpleStringProperty() }
    private val port = model.bind { SimpleIntegerProperty() }
    private val username = model.bind { SimpleStringProperty() }
    private val password = model.bind { SimpleStringProperty() }

    private val loginController: LoginController by inject()

    override val root = form {
        fieldset("Corda node host and port") {
            field {
                textfield(host) {
                    required()
                    text = "localhost"
                    maxWidth = 160.0
                }
                textfield(port) {
                    required()
                    filterInput { it.controlNewText.isInt() }
                    whenDocked { requestFocus() }
                    text = ""
                    maxWidth = 70.0
                }
            }
        }
        fieldset("User name") {
            textfield(username) {
                required()
                text = "user1"
            }
        }
        fieldset("Password") {
            passwordfield(password) {
                required()
                text = "test"
            }
        }
        button("Login to node") {
            enableWhen(model.valid)
            isDefaultButton = true
            action {
                runAsyncWithProgress {
                    loginController.login(host.value, port.value, username.value, password.value)
                }
            }
        }
        label(loginController.statusProperty) {
            isWrapText = true
            style {
                fontWeight = FontWeight.BLACK
                textFill = Color.RED
                paddingTop = 10
            }
        }
    }
}

