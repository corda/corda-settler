package com.r3.corda.finance.ripple

import com.r3.corda.finance.ripple.services.ReadWriteXRPClient
import com.r3.corda.finance.ripple.services.XRPClientForVerification
import com.r3.corda.finance.ripple.utilities.IncorrectSequenceNumberException
import com.r3.corda.finance.ripple.utilities.TransactionNotFoundException
import com.ripple.core.coretypes.AccountID
import com.ripple.core.coretypes.Amount
import com.ripple.core.coretypes.hash.Hash256
import com.ripple.core.coretypes.uint.UInt32
import com.ripple.core.types.known.tx.signed.SignedTransaction
import org.junit.Test
import java.net.URI
import kotlin.test.assertFailsWith

class TestXRPClient(override val nodeUri: URI, override val secret: String, override val address: AccountID) : ReadWriteXRPClient

class XrpClientTests {

    companion object {
        @JvmStatic
        private val nodeUri = URI("http://s.altnet.rippletest.net:51234")
        // Credentials for an account on the XRPService test net.
        @JvmStatic
        private val client = TestXRPClient(
                nodeUri = nodeUri,
                secret = "ssn8cYYksFFexYq97sw9UnvLnMKgh",
                address = AccountID.fromString("rNmkj4AtjEHJh3D9hMRC4rS3CXQ9mX4S4b")
        )
    }

    @Test
    fun `get account info`() {
        val client = XRPClientForVerification(nodeUri = nodeUri)
        val accountId = AccountID.fromString("r3kmLJN5D28dHuH8vZNUZpMC43pEHpaocV")
        client.accountInfo(accountId)
    }

    @Test
    fun `get next sequence number`() {
        client.nextSequenceNumber(AccountID.fromString("rNmkj4AtjEHJh3D9hMRC4rS3CXQ9mX4S4b"))
    }

    private fun createAndSignTx(sequenceNumber: UInt32): SignedTransaction {
        val payment = client.createPayment(
                sequence = sequenceNumber,
                source = AccountID.fromString("rNmkj4AtjEHJh3D9hMRC4rS3CXQ9mX4S4b"),
                destination = AccountID.fromString("ra6mzL1Xy9aN5eRdjzn9CHTMwcczG1uMpN"),
                amount = Amount.fromString("10000"),
                fee = Amount.fromString("1000"),
                linearId = Hash256.fromHex("B55A46422EC5BD69F21BF6C365EC86091D3C3DF73D4004A0A27FFDD6D719F8E5")
        )
        println(payment.amount())
        return client.signPayment(payment)
    }

    @Test
    fun `create, sign and submit payment successfully`() {
        val sequenceNumber = client.nextSequenceNumber(AccountID.fromString("rNmkj4AtjEHJh3D9hMRC4rS3CXQ9mX4S4b"))
        val signedTransaction = createAndSignTx(sequenceNumber)
        println(client.submitTransaction(signedTransaction))
    }

    @Test
    fun `create, sign and submit payment with incorrect sequence number`() {
        val signedTransaction = createAndSignTx(UInt32(1))
        assertFailsWith<IncorrectSequenceNumberException> { client.submitTransaction(signedTransaction) }
    }

    @Test
    fun `get transaction info for valid transaction id`() {
        println(client.transaction("58FB45D3A81B1F5AEA9E8A114056F3637312A043C24226EE4A62BE9B8051CBE2"))
    }

    @Test
    fun `get transaction info for invalid transaction id`() {
        assertFailsWith<TransactionNotFoundException> {
            client.transaction("8921B02CE76A711594601B7DD7D52FB126EBED2109FCC1979346373F26406114")
        }
    }

    @Test
    fun `check server state`() {
        println(client.serverState())
    }

    @Test
    fun `get ledger current index`() {
        println(client.ledgerIndex())
    }

}