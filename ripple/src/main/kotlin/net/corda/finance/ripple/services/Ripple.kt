package net.corda.finance.ripple.services

import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.finance.ripple.clients.RippleClientForPayment

const val configFileName = "ripple.conf"

@CordaService
class Ripple(val services: AppServiceHub) : SingletonSerializeAsToken() {
    val client: RippleClientForPayment by lazy { RippleClientForPayment(configFileName) }
}