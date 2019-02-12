package com.r3.corda.finance.swift.services

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.corda.core.node.AppServiceHub
import net.corda.core.node.services.CordaService
import net.corda.core.serialization.SingletonSerializeAsToken
import java.io.File
import java.nio.file.Paths

@CordaService
class SWIFTService(val appServiceHub : AppServiceHub) : SingletonSerializeAsToken() {
    private var _config = loadConfig()

    private fun apiUrl() = _config.getString("apiUrl")
            ?: throw IllegalArgumentException("apiUrl must be provided")

    private fun apiKey() = _config.getString("apiKey")
            ?: throw IllegalArgumentException("apiKey must be provided")

    /**
     * Attempts to load service configuration from cordapps/config with a fallback to classpath
     */
    private fun loadConfig() : Config {
        val fileName = "swift.conf"
        val defaultLocation = (Paths.get("cordapps").resolve("config").resolve(fileName)).toFile()
        return if (defaultLocation.exists()) ConfigFactory.parseFile(defaultLocation)
        else ConfigFactory.parseFile(File(SWIFTClient::class.java.classLoader.getResource(fileName).toURI()))
    }

    private fun debtorName() = _config.getString("debtorName")
            ?: throw IllegalArgumentException("debtorName must be provided")

    private fun debtorLei() = _config.getString("debtorLei")
            ?: throw IllegalArgumentException("debtorLei must be provided")

    private fun debtorIban() = _config.getString("debtorIban")
            ?: throw IllegalArgumentException("debtorIban must be provided")

    private fun debtorBicfi() = _config.getString("debtorBicfi")
            ?: throw IllegalArgumentException("debtorBicfi must be provided")


    fun swiftClient() = SWIFTClient(apiUrl(), apiKey(), debtorName(), debtorLei(), debtorIban(), debtorBicfi())
}