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

class UpdatePaymentStatusManuallyTests {
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
            ManualSettlement("Checking", "Pay to the order of 'R3'"),
            listOf(ManualPayment("1", Amount(10000, currency)))
    )

    @Test
    fun `update payment status succeeds`() {
        ledger.ledger {
            transaction {
                attachment(contractId)
                input(contractId, obligation)
                output(contractId, obligation.copy(payments = listOf(ManualPayment("1", 100 of currency, PaymentStatus.SETTLED))))
                command(listOf(alice.publicKey, bob.publicKey), ObligationCommands.UpdatePayment("1"))
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
                output(contractId, obligation.copy(payments = listOf(ManualPayment("1", 100 of currency, PaymentStatus.SETTLED)),
                        faceAmount = 10 of currency
                ))
                command(listOf(alice.publicKey, bob.publicKey), ObligationCommands.UpdatePayment("1"))
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
                output(contractId, obligation.copy(payments = listOf(ManualPayment("1", 100 of currency, PaymentStatus.SETTLED)),
                        linearId = newId
                ))
                command(listOf(alice.publicKey, bob.publicKey), ObligationCommands.UpdatePayment("1"))
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
                output(contractId, obligation.copy(payments = listOf(ManualPayment("1", 100 of currency, PaymentStatus.SETTLED)),
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
                output(contractId, obligation.copy(payments = listOf(ManualPayment("1", 100 of currency, PaymentStatus.SETTLED)),
                        obligee = charlie.party
                ))
                command(listOf(alice.publicKey, bob.publicKey), ObligationCommands.UpdatePayment("1"))
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
                output(contractId, obligation.copy(payments = listOf(ManualPayment("1", 100 of currency, PaymentStatus.SETTLED)),
                        dueBy = now
                ))
                command(listOf(alice.publicKey, bob.publicKey), ObligationCommands.UpdatePayment("1"))
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
                output(contractId, obligation.copy(payments = listOf(ManualPayment("1", 100 of currency, PaymentStatus.SETTLED)),
                        createdAt = now.plusMillis(100L)
                ))
                command(listOf(alice.publicKey, bob.publicKey), ObligationCommands.UpdatePayment("1"))
                `fails with`("Property invariant failed between input and output for field createdAt: ${obligation.createdAt} -> ${obligation.createdAt.plusMillis(100L)}")
            }
        }
    }

    @Test
    fun `make manual payment fails with no settlement method`() {
        ledger.ledger {
            transaction {
                attachment(contractId)
                input(contractId, obligation.copy(payments = listOf(ManualPayment("1", 100 of currency, PaymentStatus.SETTLED))))
                output(contractId, obligation.copy(payments = listOf(ManualPayment("1", 100 of currency, PaymentStatus.SETTLED))))
                command(listOf(alice.publicKey, bob.publicKey), ObligationCommands.UpdatePayment("1"))
                `fails with`("Only payments with a SENT status can be updated")
            }
        }
    }

    @Test
    fun `make manual payment fails with different payment amounts`() {
        ledger.ledger {
            transaction {
                attachment(contractId)
                input(contractId, obligation)
                output(contractId, obligation.copy(payments = listOf(ManualPayment("1", 100.01 of currency, PaymentStatus.SETTLED))))
                command(listOf(alice.publicKey, bob.publicKey), ObligationCommands.UpdatePayment("1"))
                `fails with`("Updated payments must have same amounts.")
            }
        }
    }

    @Test
    fun `make manual payment fails with different payment references`() {
        ledger.ledger {
            transaction {
                attachment(contractId)
                input(contractId, obligation)
                output(contractId, obligation.copy(payments = listOf(ManualPayment("2", 100 of currency, PaymentStatus.SETTLED))))
                command(listOf(alice.publicKey, bob.publicKey), ObligationCommands.UpdatePayment("1"))
                `fails with`("Collection contains no element matching the predicate")
            }
        }
    }

    @Test
    fun `make manual payment fails with extra payments`() {
        ledger.ledger {
            transaction {
                attachment(contractId)
                input(contractId, obligation)
                output(contractId, obligation.copy(payments = listOf(
                        ManualPayment("1", Amount(10000, currency), PaymentStatus.SETTLED),
                        ManualPayment("2", Amount(10001, currency), PaymentStatus.SETTLED)
                )))
                command(listOf(alice.publicKey, bob.publicKey), ObligationCommands.UpdatePayment("1"))
                `fails with`("Input and output obligations must have same number of payments.")
            }
        }
    }
}