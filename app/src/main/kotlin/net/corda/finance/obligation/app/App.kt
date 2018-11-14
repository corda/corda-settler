package net.corda.finance.obligation.app

import tornadofx.*

fun main(args: Array<String>) = launch<ObligationApp>(args)

class ObligationApp : App(LoginView::class)