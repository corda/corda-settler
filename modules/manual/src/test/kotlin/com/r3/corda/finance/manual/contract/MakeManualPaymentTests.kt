package com.r3.corda.finance.manual.contract

import com.r3.corda.finance.manual.types.ManualPayment
import com.r3.corda.finance.manual.types.ManualSettlement
import com.r3.corda.finance.obligation.contracts.ObligationContract
import com.r3.corda.finance.obligation.contracts.commands.ObligationCommands
import com.r3.corda.finance.obligation.contracts.states.Obligation
import com.r3.corda.finance.obligation.contracts.types.PaymentStatus
import com.r3.corda.lib.tokens.contracts.utilities.of
import com.r3.corda.lib.tokens.money.FiatCurrency
import net.corda.core.contracts.Amount
import net.corda.core.contracts.UniqueIdentifier
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.CHARLIE_NAME
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test
import java.time.Instant

class MakeManualPaymentTests {
    private val ledger = MockServices()
    private val alice = TestIdentity(ALICE_NAME)
    private val bob = TestIdentity(BOB_NAME)
    private val charlie = TestIdentity(CHARLIE_NAME)
    private val contractId = ObligationContract.CONTRACT_REF

    private val now = Instant.now()

    private val currency = FiatCurrency.getInstance("CAD")

    private val obligation = Obligation(
            Amount(10000, currency),
            alice.party,
            bob.party,
            null,
            now,
            ManualSettlement("Checking", "Pay to the order of 'R3'")
    )

    @Test
    fun `make manual payment succeeds`() {
        ledger.ledger {
            transaction {
                attachment(contractId)
                input(contractId, obligation)
                output(contractId, obligation.withPayment(ManualPayment("1", 100 of currency)))
                command(listOf(alice.publicKey, bob.publicKey), ObligationCommands.AddPayment("1"))
                verifies()
            }
        }
    }

    @Test
    fun `make manual payment with different faceAmount fails`() {
        ledger.ledger {
            transaction {
                attachment(contractId)
                input(contractId, obligation)
                output(contractId, obligation.withPayment(ManualPayment("1", 100 of currency)).copy(
                        faceAmount = Amount(1000, currency)
                ))
                command(listOf(alice.publicKey, bob.publicKey), ObligationCommands.AddPayment("1"))
                `fails with`("Property invariant failed between input and output for field faceAmount: 100.00 TokenType(tokenIdentifier='CAD', fractionDigits=2) -> 10.00 TokenType(tokenIdentifier='CAD', fractionDigits=2)")
            }
        }
    }

    @Test
    fun `make manual payment with different linearId fails`() {
        val newId = UniqueIdentifier()
        ledger.ledger {
            transaction {
                attachment(contractId)
                input(contractId, obligation)
                output(contractId, obligation.withPayment(ManualPayment("1", 100 of currency)).copy(
                        linearId = newId
                ))
                command(listOf(alice.publicKey, bob.publicKey), ObligationCommands.AddPayment("1"))
                `fails with`("Property invariant failed between input and output for field linearId: ${obligation.linearId} -> $newId")
            }
        }
    }

    @Test
    fun `make manual payment with different obligor fails`() {
        ledger.ledger {
            transaction {
                attachment(contractId)
                input(contractId, obligation)
                output(contractId, obligation.withPayment(ManualPayment("1", 100 of currency)).copy(
                        obligor = charlie.party
                ))
                command(listOf(alice.publicKey, bob.publicKey), ObligationCommands.AddPayment("1"))
                `fails with`("Property invariant failed between input and output for field obligor: ${alice.party} -> ${charlie.party}")
            }
        }
    }

    @Test
    fun `make manual payment with different obligee fails`() {
        ledger.ledger {
            transaction {
                attachment(contractId)
                input(contractId, obligation)
                output(contractId, obligation.withPayment(ManualPayment("1", 100 of currency)).copy(
                        obligee = charlie.party
                ))
                command(listOf(alice.publicKey, bob.publicKey), ObligationCommands.AddPayment("1"))
                `fails with`("Property invariant failed between input and output for field obligee: ${bob.party} -> ${charlie.party}")
            }
        }
    }

    @Test
    fun `make manual payment with different dueBy fails`() {
        ledger.ledger {
            transaction {
                attachment(contractId)
                input(contractId, obligation)
                output(contractId, obligation.withPayment(ManualPayment("1", 100 of currency)).copy(
                        dueBy = now
                ))
                command(listOf(alice.publicKey, bob.publicKey), ObligationCommands.AddPayment("1"))
                `fails with`("Property invariant failed between input and output for field dueBy: null -> $now")
            }
        }
    }

    @Test
    fun `make manual payment with different createdAt fails`() {
        ledger.ledger {
            transaction {
                attachment(contractId)
                input(contractId, obligation)
                output(contractId, obligation.withPayment(ManualPayment("1", 100 of currency)).copy(
                        createdAt = now.plusMillis(100L)
                ))
                command(listOf(alice.publicKey, bob.publicKey), ObligationCommands.AddPayment("1"))
                `fails with`("Property invariant failed between input and output for field createdAt: ${obligation.createdAt} -> ${obligation.createdAt.plusMillis(100L)}")
            }
        }
    }

    @Test
    fun `make manual payment fails with no settlement method`() {
        ledger.ledger {
            transaction {
                attachment(contractId)
                input(contractId, obligation.copy(settlementMethod = null))
                output(contractId, obligation.copy(settlementMethod = null).withPayment(ManualPayment("1", Amount(10000, currency))))
                command(listOf(alice.publicKey, bob.publicKey), ObligationCommands.AddPayment("1"))
                `fails with`("There must be a settlement method specified before payments can be registered against this obligation")
            }
        }
    }

    @Test
    fun `make manual payment fails with no payment added to list`() {
        ledger.ledger {
            transaction {
                attachment(contractId)
                input(contractId, obligation)
                output(contractId, obligation)
                command(listOf(alice.publicKey, bob.publicKey), ObligationCommands.AddPayment("1"))
                `fails with`("You can only add one payment at once")
            }
        }
    }

    @Test
    fun `make manual payment fails with two payments added to list`() {
        ledger.ledger {
            transaction {
                attachment(contractId)
                input(contractId, obligation)
                output(contractId, obligation.withPayment(ManualPayment("1", 50 of currency)).withPayment(ManualPayment("2", Amount(5000, currency))))
                command(listOf(alice.publicKey, bob.publicKey), ObligationCommands.AddPayment("1"))
                `fails with`("You can only add one payment at once")
            }
        }
    }

    @Test
    fun `make manual payment fails with SETTLED payment added to list`() {
        ledger.ledger {
            transaction {
                attachment(contractId)
                input(contractId, obligation)
                output(contractId, obligation.withPayment(ManualPayment("1", 50 of currency, PaymentStatus.FAILED)))
                command(listOf(alice.publicKey, bob.publicKey), ObligationCommands.AddPayment("1"))
                `fails with`("Payments can only be added with a SENT status.")
            }
        }
    }
}