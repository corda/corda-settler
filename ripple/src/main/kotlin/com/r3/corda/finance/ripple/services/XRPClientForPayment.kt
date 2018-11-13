package com.r3.corda.finance.ripple.services

import com.ripple.core.coretypes.AccountID
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import java.net.URI

open class XRPClientForPayment(configFileName: String) : ReadWriteXRPClient {

    private val config: Config = ConfigFactory.parseResources(configFileName)
    override val nodeUri: URI get() = URI(config.getString("server"))
    override val secret: String get() = config.getString("secret")
    override val address: AccountID get() = AccountID.fromString(config.getString("address"))

}

