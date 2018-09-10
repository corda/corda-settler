package net.corda.finance.ripple.services

import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.finance.ripple.RippleClientForPayment

@CordaService
class RippleService(val services: AppServiceHub) : SingletonSerializeAsToken() {
    private val configFileName = "ripple.conf"
    val client: RippleClientForPayment by lazy { RippleClientForPayment(configFileName) }
}