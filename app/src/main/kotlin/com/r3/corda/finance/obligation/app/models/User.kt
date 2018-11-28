package com.r3.corda.finance.obligation.app.models

import javafx.beans.property.SimpleStringProperty
import tornadofx.*

class User(username: String? = null, hostAndPort: String? = null) {
    val usernameProperty = SimpleStringProperty(this, "username", username)
    var username by usernameProperty

    val hostAndPortProperty = SimpleStringProperty(this, "hostAndPort", hostAndPort)
    var hostAndPort by hostAndPortProperty
}

class UserModel : ItemViewModel<User>() {
    val username = bind(User::usernameProperty)
    val hostAndPort = bind(User::hostAndPortProperty)
}