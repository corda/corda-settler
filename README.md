# Obligation settler

## Requirements

Obligations which arise on a Corda ledger can be settled individually,
in whole, with of ledger payments, in this case XRP. Example: Alice
incurs an obligation to pay Bob an amount of USD at some point in the
future. Alice should only be able to mark the obligation as paid if she
can prove that the required amount of XRP was paid to Bob via the XRP
ledger.

## Design

**The obligation contract**

I'll use the obligation contract I created of Ubin as a template but
will add an additional property called `settlementInstructions` of some
interface type.

Settlement can either be on-ledger or off-ledger. For on-ledger we can
specify token states from which issuers are acceptable. Note, that this
is not implemented in this project. For off-ledger, there will be only
one option for now: XRP settlement. Different implementations can be
used for each method. The settler CorDapp is settlement rail agnostic.
To add support for more rails just add a CorDapp that sub-classes the
payment flow and adds settlement instructions for that settlement type.

In addition. I'll create a flow which allows the parties to the
obligation to add settlement instructions. For now, this flow will start
with the obligee as they are required to add a Ripple address to which
they expect the settlement payment to be made.

The Corda contract which governs how the obligation functions, allows
the obligation to be marked as fulfilled if an Oracle signs a Corda
transaction attesting to the fact that a payment occurred on the Ripple
ledger as specified by the settlement instructions in the obligation.

I'm sure this will not be the final design for obligation but it feels
like a good approach for now just to get something done.

**Step 1. Creating the obligation.**

Alice and Bob record that Alice has incurred an obligation to pay Bob an
amount of currency. The obligation specifies which token type the
financial obligation is in respect of. For now, this will just be a new
`DigitalCurrency` type (set to "XRP") which emulates the
`java.util.Currency` class. The obligation will also specify a payment
deadline as an `Instant`.

Also, we must know if the settlement payment of XRP succeeded or failed
within some bounded time, otherwise we expose ourselves to a "halting
problem". As such, we will need to add the ledger number of the Ripple
ledger which we expect payment to be made by. This can be added after
the payment instruction has been sent to a Ripple node.

In terms of settlement instructions for off-ledger XRP payments, we'll
need:

* which Ripple address Bob expects Alice to make a payment to
* which Ripple ledger number the payment should be expected by - this is
  effectively the deadline
* a UUID to track the Ripple payment - this should be the linear ID of
  the obligation the payment will extinguish
* which Corda Oracle should be used to sign that the payment occurred

Out of these items, only the Ripple address, UUID and Oracle need to be
known before the payment is made. The others can be added subsequently
to the payment being submitted to a Ripple node.

Of course, both Alice and Bob must agree on the size of the obligation
and this may happen before the settlement instructions are added. I'll
add a command to the obligation which allows the participants to add
settlement instructions at any point after the obligation has been
created.

The transaction creating the obligation (with settlement details) is
then sent to a notary for ordering (it will include a timestamp) and
then committed by both Alice and Bob to their node's local storage.

**Step 2. Adding settlement instructions.**

Bob will start a flow to add his Ripple address to the settlement
instructions. He'll also add a UUID (which will be added to the Ripple
transaction by Alice) that matches the UUID portion of the Linear ID.
Bob will also specify which Oracle will be used. Alice is to agree on
the Oracle.

The Ripple ledger number can be left blank for now.

At this point, both parties are in consensus that an XRP payment is
owing, precisely how it should be made and what evidence the oracle will
require from the Ripple ledger in order to be prepared to sign a
statement that the payment has indeed been made.

**Step 3. Making the Ripple payment.**

Alice creates a new Ripple payment transaction for the specified amount,
to the specified account, with the specified UUID (in the memo field of
the Ripple transaction).

Alice then signs the transaction and submits it to her rippled process
or a process available elsewhere via RPC, web-sockets, whatever.

She receives the transaction hash for the newly submitted transaction.
Assuming the transaction is properly formed, the node commits the
transaction to its current version of the ledger and broadcasts the
transaction to other nodes on the Ripple network.

At any point after the Ripple transaction was submitted to rippled, the
transaction hash can be used to query the specified Oracle.

Assuming the new transaction is included in the calculated ledgers of a
super-majority of nodes on the Ripple network, then the transaction will
be included in a validated ledger instance.

The XRP payment is actually made via a Corda flow which calls out
to a Ripple node. When Alice receives the transaction hash, she adds
it to the Corda obligation along with a ledger number which the payment
should be expected by. We do this because it is easier for the Oracle
to query for a specific transaction hash rather than a list of
transactions in a Ripple account. It also means that the obligee now
knows that the payment instruction has been sent into the Ripple
network.

Based on the Ripple developer docs. It should take only a couple of
ledger numbers for a payment to be included in a validated ledger.
We can experiment with numbers but for now I'll assert that the payment
should succeed after 10 ledgers from when the payment was initially
made.

**Step 3. Extinguishing the obligation.**

The obligation can be submitted to the Oracle at any point after the
Ripple transaction hash has been added to obligation.

For any obligation to be settled via off ledger XRP payment, the Oracle
will sign a Corda transaction exiting the obligation if the Ripple
transaction represented by the hash has:

* transactionResult: tesSUCCESS and validated: true
* an amount paid equal to the amount specified in the obligation (NOTE:
  this may be less due to fees... Investigate)
* the account specified in the settlement instructions as the recipient
* a UUID in the transaction memo field, equal to the UUID specified in
  the settlement instructions

The server_state of the rippled process used by the Oracle must be
set to FULL.

Corda transaction proposals can be sent to the Oracle at any time for
signing. The Oracle is configured to cache transaction proposals until
the specified Ripple transaction is

a) included in a validated Ripple ledger instance, or;
b) the last permissible Ripple ledger has been closed.

As such, if the the Ripple ledger_index exceeds the specified
LastLedgerSequence then the Oracle will return an exception for the
given transaction hash, indicating that the window for committing the
transaction has passed.

At this point, new settlement instructions can be added, if necessary.

In practise, the Oracle would run a rippled process where the unique
node list ("UNL") for the node would be published for participants on
Corda Network to inspect. The Oracle's Corda node would interact with
the rippled process via web-sockets or JSON-RPC. However, for the
purposes of this PoC, the Oracle will use a publicly available Ripple
node.

When the Oracle signs, as per the obligation contract code, the
obligation can be extinguished. The Oracle sends back the signature over
the transaction proposal. At this point, either Alice or Bob can send
the obligation to the Notary for notarisation. NOTE: Alice and Bob do
not need to sign.

## Notes/Issues

* We use xrp ledger number to specify when the payment should be made by.
  Note that each ledger number takes about 3-5 seconds.
* The unit tests require Internet connectivity specifically, you need to
  be able to access the Ripple testnet node and the exchange rate
  provider.
* The Unit tests require you to use an XRP account that has enough XRP
  to make the payments. If you specified account in `xrp.conf` has ran
  out of XRP then get a new one here [here](https://developers.ripple.com/xrp-test-net-faucet.html).
* For now, the unit tests require that you have an account with
  http://cryptocompare.com to get the XRP/USD exchange rate. NOTE: There
  is no affiliation to this provider, it was just the first one I found
  that worked easily! Feel free to replace it with your own provider. The
  exchange rate oracle for this project is really just a stub so we can
  handle token novation.

## TODO

* Define a settlement Oracle interface/abstract class and move reusable
  parts of the `XrpOracleService` to the abstract class.
* Add the option to use a mock Fx Oracle that just provides a single
  fixed Fx rate.



