package com.r3.corda.finance.obligation.app

import com.r3.corda.finance.obligation.app.views.LoginView
import tornadofx.*

fun main(args: Array<String>) = launch<ObligationApp>(args)

class ObligationApp : App(LoginView::class)