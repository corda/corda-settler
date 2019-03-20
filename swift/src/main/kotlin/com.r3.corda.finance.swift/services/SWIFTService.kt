package com.r3.corda.finance.swift.services

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import sun.security.provider.X509Factory
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*

@CordaService
class SWIFTService(val appServiceHub : AppServiceHub) : SingletonSerializeAsToken() {
    companion object {
        // TODO: this should be driven by configuration parameter
        fun privateKey() : PrivateKey {
            val fileContents = String(Files.readAllBytes(Paths.get(SWIFTService::class.java.classLoader.getResource("swiftKey.pem").toURI())))
            val decodedContents = Base64.getDecoder().decode(fileContents.replace("\\n".toRegex(), "").replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", ""))
            val keyFactory = KeyFactory.getInstance("RSA")
            val keySpecPKCS8 = PKCS8EncodedKeySpec(decodedContents)
            return keyFactory.generatePrivate(keySpecPKCS8)
        }

        // TODO: this should be driven by configuration parameter
        fun certificate() : X509Certificate {
            val certFactory = CertificateFactory.getInstance("X.509")
            val fileContents = String(Files.readAllBytes(Paths.get(SWIFTService::class.java.classLoader.getResource("swiftCert.pem").toURI())))
            val decodedContents = Base64.getDecoder().decode(fileContents.replace(X509Factory.BEGIN_CERT, "").replace(X509Factory.END_CERT, "").replace("\\n".toRegex(), ""))
            return certFactory.generateCertificate(ByteArrayInputStream(decodedContents)) as X509Certificate
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

    fun swiftClient() = SWIFTClient(apiUrl, apiKey, privateKey(), certificate())
}