package net.corda.finance.obligation.oracle.services

import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken

@CordaService
class Service(val services: AppServiceHub) : SingletonSerializeAsToken() {

}