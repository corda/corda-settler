package com.r3.corda.finance.swift.services

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.core.crypto.Crypto
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import sun.misc.BASE64Decoder
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.security.PrivateKey

@CordaService
class SWIFTService(val appServiceHub : AppServiceHub) : SingletonSerializeAsToken() {
    companion object {
        // TODO: this should be driven by configuration parameter
        fun privateKey() : PrivateKey {
            val key = BASE64Decoder().decodeBuffer(String(Files.readAllBytes(Paths.get(SWIFTService::class.java.classLoader.getResource("swiftKey.pem").toURI()))))
            return Crypto.decodePrivateKey(Crypto.RSA_SHA256, key)
        }
    }

    private var _config = loadConfig()

    private val apiUrl : String
        get() = _config.getString("apiUrl") ?: throw IllegalArgumentException("apiUrl must be provided")

    private val apiKey : String
        get() = _config.getString("apiKey") ?: throw IllegalArgumentException("apiKey must be provided")

    val debtorName : String
        get() = _config.getString("debtorName") ?: throw IllegalArgumentException("debtorName must be provided")

    val debtorLei : String
        get() = _config.getString("debtorLei") ?: throw IllegalArgumentException("debtorLei must be provided")

    val debtorIban : String
        get() = _config.getString("debtorIban") ?: throw IllegalArgumentException("debtorIban must be provided")

    val debtorBicfi : String
        get() = _config.getString("debtorBicfi") ?: throw IllegalArgumentException("debtorBicfi must be provided")

    /**
     * Attempts to load service configuration from cordapps/config with a fallback to classpath
     */
    private fun loadConfig() : Config {
        val fileName = "swift.conf"
        val defaultLocation = (Paths.get("cordapps").resolve("config").resolve(fileName)).toFile()
        return if (defaultLocation.exists()) ConfigFactory.parseFile(defaultLocation)
        else ConfigFactory.parseFile(File(SWIFTClient::class.java.classLoader.getResource(fileName).toURI()))
    }

    fun swiftClient() = SWIFTClient(apiUrl, apiKey, privateKey())
}