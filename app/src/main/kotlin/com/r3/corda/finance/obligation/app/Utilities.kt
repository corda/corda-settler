package com.r3.corda.finance.obligation.app

import com.r3.corda.finance.obligation.types.DigitalCurrency
import com.r3.corda.finance.obligation.types.FiatCurrency
import javafx.collections.ObservableList
import javafx.util.StringConverter
import net.corda.client.jfx.model.Models
import net.corda.client.jfx.model.NetworkIdentityModel
import net.corda.client.jfx.utils.map
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.Amount
import net.corda.core.contracts.ContractState
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.node.NodeInfo
import net.corda.core.utilities.NetworkHostAndPort
import tornadofx.*
import java.math.BigDecimal
import java.util.*

fun <T : ContractState, U : Any> ObservableList<T>.transform(block: (T) -> U) = map { block(it) }

inline fun <reified M : Any> UIComponent.getModel(): M = Models.get(M::class, this.javaClass.kotlin)

fun AbstractParty.toKnownParty() = Models.get(NetworkIdentityModel::class, javaClass.kotlin).partyFromPublicKey(this.owningKey).value?.legalIdentitiesAndCerts?.first()?.party

fun NodeInfo.singleIdentityAndCert(): PartyAndCertificate = legalIdentitiesAndCerts.single()

fun connectToCordaRpc(hostAndPort: String, username: String, password: String): CordaRPCOps {
    println("Connecting to Issuer node $hostAndPort.")
    val nodeAddress = NetworkHostAndPort.parse(hostAndPort)
    val client = CordaRPCClient(nodeAddress)
    return client.start(username, password).proxy
}

fun <T> stringConverter(fromStringFunction: ((String?) -> T)? = null, toStringFunction: (T) -> String): StringConverter<T> {
    return object : StringConverter<T>() {
        override fun fromString(string: String?): T {
            return fromStringFunction?.invoke(string) ?: throw UnsupportedOperationException("not implemented")
        }

        override fun toString(o: T): String {
            return toStringFunction(o)
        }
    }
}

object PartyNameFormatter {
    val short = object : Formatter<CordaX500Name> {
        override fun format(value: CordaX500Name) = value.organisation
    }

    val full = object : Formatter<CordaX500Name> {
        override fun format(value: CordaX500Name): String = value.toString()
    }
}

interface Formatter<in T> {
    fun format(value: T): String
}

fun formatAmount(amount: Amount<*>): String {
    val token = amount.token
    return when (token) {
        is DigitalCurrency -> {
            val symbol = token.symbol
            val quantity = BigDecimal.valueOf(amount.quantity, 0) * token.displayTokenSize
            "$symbol $quantity"
        }
        is FiatCurrency -> amount.toString()
        else -> throw UnsupportedOperationException("Only FiatCurrency and DigitalCurrency are supported by the " +
                "Corda settler UI.")
    }
}