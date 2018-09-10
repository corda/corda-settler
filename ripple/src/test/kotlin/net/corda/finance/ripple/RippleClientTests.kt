package net.corda.finance.ripple

import com.ripple.core.coretypes.AccountID
import com.ripple.core.coretypes.Amount
import com.ripple.core.coretypes.hash.Hash256
import org.junit.Test
import java.net.URI

class TestRippleClient(override val nodeUri: URI, override val secret: String, override val address: AccountID) : ReadWriteRippleClient

class RippleClientTests {

    companion object {
        @JvmStatic
        private val nodeUri = URI("http://s.altnet.rippletest.net:51234")
        // Credentials for an account on the RippleService test net.
        @JvmStatic
        private val client = TestRippleClient(
                nodeUri = nodeUri,
                secret = "ssn8cYYksFFexYq97sw9UnvLnMKgh",
                address = AccountID.fromString("rNmkj4AtjEHJh3D9hMRC4rS3CXQ9mX4S4b")
        )
    }

    @Test
    fun `get next sequence number`() {
        val result = client.nextSequenceNumber(AccountID.fromString("rNmkj4AtjEHJh3D9hMRC4rS3CXQ9mX4S4b"))
        println(result)
    }

    @Test
    fun `create, sign and submit payment`() {
        val payment = client.createPayment(
                source = AccountID.fromString("rNmkj4AtjEHJh3D9hMRC4rS3CXQ9mX4S4b"),
                destination = AccountID.fromString("ra6mzL1Xy9aN5eRdjzn9CHTMwcczG1uMpN"),
                amount = Amount.fromString("100000"),
                fee = Amount.fromString("1000"),
                linearId = Hash256.fromHex("B55A46422EC5BD69F21BF6C365EC86091D3C3DF73D4004A0A27FFDD6D719F8E5")
        )
        val signedPayment = client.signPayment(payment)
        val result = client.submitTransaction(signedPayment)
        println(result)
    }

    @Test
    fun `get transaction info`() {
        val result = client.transaction("8921B02CE76A711594601B7DD7D52FB126EBED2109FCC1979346373F26406113")
        println(result)
    }

    @Test
    fun `check server state`() {
        val result = client.serverState()
        println(result)
    }

}