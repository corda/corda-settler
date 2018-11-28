package com.r3.corda.finance.obligation.app

import com.r3.corda.finance.obligation.app.views.LoginView
import javafx.scene.text.Font
import tornadofx.*

fun main(args: Array<String>) = launch<ObligationApp>(args)

class ObligationApp : App(LoginView::class, MyStylesheet::class)

class MyStylesheet : Stylesheet() {
    init {
        root {
            fontFamily = "Tahoma"
        }
    }
}

//fun main(args: Array<String>) {
//    Font.getFamilies().forEach(System.out::println)
//}