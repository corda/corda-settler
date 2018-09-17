package net.corda.finance.obligation.app

import javafx.application.Application
import tornadofx.*

fun main(args: Array<String>) = Application.launch(ObligationApp::class.java, *args)

class ObligationApp : App(LoginView::class)