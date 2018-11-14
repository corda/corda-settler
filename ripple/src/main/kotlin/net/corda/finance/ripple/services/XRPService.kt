package net.corda.finance.ripple.services

import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.finance.ripple.XRPClientForPayment

/** Provides access to a read/write XRP client, which can make and sign payment transactions. */
@CordaService
class XRPService(val services: AppServiceHub) : SingletonSerializeAsToken() {
    // Config file defaulted to this name.
    private val configFileName = "xrp.conf"
    val client: XRPClientForPayment by lazy { XRPClientForPayment(configFileName) }
}