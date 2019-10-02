package com.r3.corda.lib.settler.ripple

import com.r3.corda.lib.settler.ripple.services.ReadWriteXRPClient
import com.r3.corda.lib.settler.ripple.services.XRPClientForVerification
import com.r3.corda.lib.settler.ripple.types.IncorrectSequenceNumberException
import com.r3.corda.lib.settler.ripple.types.TransactionNotFoundException
import com.r3.corda.lib.settler.ripple.utilities.toXRPAmount
import com.ripple.core.coretypes.AccountID
import com.ripple.core.coretypes.Amount
import com.ripple.core.coretypes.Currency.XRP
import com.ripple.core.coretypes.hash.Hash256
import com.ripple.core.coretypes.uint.UInt32
import com.ripple.core.types.known.tx.signed.SignedTransaction
import org.junit.Ignore
import org.junit.Test
import java.math.BigDecimal
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import net.corda.core.contracts.Amount as CordaAmount

class TestXRPClient(override val nodeUri: URI, override val secret: String, override val address: AccountID) : ReadWriteXRPClient

class XrpClientTests {

    companion object {
        @JvmStatic
        private val nodeUri = URI("http://s.altnet.rippletest.net:51234")
        // Credentials for an account on the XRPService test net.
        @JvmStatic
        private val client = TestXRPClient(
                nodeUri = nodeUri,
                secret = "snJ9VCQ2pkDrZTGa5WVBbFnWEaHVB",
                address = AccountID.fromString("rsH5JfiWwEpcmFyWrK8xPCeXVGALTgww4w")
        )
    }

    @Test
    fun `get account info`() {
        val client = XRPClientForVerification(nodeUri = nodeUri)
        val accountId = AccountID.fromString("rsH5JfiWwEpcmFyWrK8xPCeXVGALTgww4w")
        client.accountInfo(accountId)
    }

    @Test
    fun `get next sequence number`() {
        client.nextSequenceNumber(AccountID.fromString("rsH5JfiWwEpcmFyWrK8xPCeXVGALTgww4w"))
    }

    private fun createAndSignTx(sequenceNumber: UInt32): SignedTransaction {
        val payment = client.createPayment(
                sequence = sequenceNumber,
                source = AccountID.fromString("rsH5JfiWwEpcmFyWrK8xPCeXVGALTgww4w"),
                destination = AccountID.fromString("rwiFDaFBAKidbEHgnE5rL9GtVJiybeTyw8"),
                amount = Amount.fromString("10000"),
                fee = Amount.fromString("1000"),
                linearId = Hash256.fromHex("B55A46422EC5BD69F21BF6C365EC86091D3C3DF73D4004A0A27FFDD6D719F8E5")
        )
        println(payment.amount())
        return client.signPayment(payment)
    }

    @Test
    fun `create, sign and submit payment successfully`() {
        val sequenceNumber = client.nextSequenceNumber(AccountID.fromString("rsH5JfiWwEpcmFyWrK8xPCeXVGALTgww4w"))
        val signedTransaction = createAndSignTx(sequenceNumber)
        println(client.submitTransaction(signedTransaction))
    }

    @Test
    fun `create, sign and submit payment with incorrect sequence number`() {
        val signedTransaction = createAndSignTx(UInt32(1))
        assertFailsWith<IncorrectSequenceNumberException> { client.submitTransaction(signedTransaction) }
    }

    @Ignore("Flakey test as Ripple reset the testnet every month so this will eventually fail.")
    @Test
    fun `get transaction info for valid transaction id`() {
        println(client.transaction("FB54C91FEC987B405F139E9B6216CD90968E48A1CAEC18482E812D54769B7C10"))
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

    @Test
    fun `corda to ripple amount`() {
        val oneDrop = CordaAmount.fromDecimal(BigDecimal("0.000001"), XRP)
        var cordaAmount = CordaAmount.zero(XRP)
        (1..1000000).forEach { cordaAmount += oneDrop }
        val xrpAmount = cordaAmount.toXRPAmount()
        val normalisedCordaAmount = cordaAmount.displayTokenSize * BigDecimal.valueOf(cordaAmount.quantity)
        assertEquals(normalisedCordaAmount.toLong(), xrpAmount.toLong())
    }

}
