package com.r3.corda.finance.ripple.services

import com.typesafe.config.ConfigException
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken

/** Provides access to a read/write XRP client, which can make and sign payment transactions. */
@CordaService
class XRPService(val services: AppServiceHub) : SingletonSerializeAsToken() {
    // Config file defaulted to this name.
    private val configFileName = "xrp.conf"
    val client: XRPClientForPayment by lazy {
        try {
            XRPClientForPayment(configFileName)
        } catch (e: ConfigException) {
            throw IllegalArgumentException(e)
        }
    }
}