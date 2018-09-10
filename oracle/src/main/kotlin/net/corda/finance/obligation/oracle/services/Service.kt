package net.corda.finance.obligation.oracle.services

import com.typesafe.config.ConfigFactory
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import java.net.URI

const val configFileName = "ripple.conf"

@CordaService
class Service(val services: AppServiceHub) : SingletonSerializeAsToken() {
    val nodes by lazy { ConfigFactory.parseResources(configFileName).getStringList("nodes").mapNotNull(::URI) }
}