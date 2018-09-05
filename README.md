# Obligation settler

TODO:

0. Figure out what the obligation contract and state looks like. it can be
   settled on or off ledger. either way we need some settlement instruction
   which includes what needs to happen. Which currency or which mechanism for settlement

    on:
        - currency
        - digital currency
        - barter of some token type
    off:
        - SWIFT
        - Ripple
            - UUID
            - Oracle
            - Ripple ledger number
            - payment address
            - NOTE: much of this can be abstracted into an interface


1. figure out how to query a ripple server on the internet.
2. build this in to a web api of our own.
3. figure out what the process of submitting and tracking the ripple
   transaction looks like

currently we use the ledger number as the timeout on the transaction but
we do not know when this ledger number will occur in time. Instead we
probably just need to use an instant in time. We can still add a ledger
number which estimates a day from now or whatever.

The ripple API gives us back a transaction hash. However, the transaction
we submit still has a UUID in it

Options for doing this:

1. create obligation.
2. Add settlement instructions by updating the obligation
3. Make the ripple payment (and get back a hash)
4. Add the hash to the settlement instructions (update the obligation)
5. Submit the obligation to the oracle.

Questions:

1. Who creates the obligation?
2. Who needs to sign when the settlement details are added?
3. How is the ripple payment made? Through a flow. Corda independent?
   Via a flow would be nice. Could call into some API then update the
   obligation with the ripple transaction hash.

   Can we rely on the ripple transaction hash only? I don't think so as
   the payment could be for the right amount but from a different party.
   So we still need the UUID which is generated when the obligation
   settlement instructions are added. The UUID should really be the
   LinearID of the obligation.

   The hash doesn't HAVE to be added. However it makes things easier
   if we do add it. As we can search for the transaction by hash.

4. What is the best way to query for the transaction?
5. how doe fees affect the payment amount?


Amendments:

* Add settlement instructions separately.

## Requirements

Obligations which arise on a Corda ledger can be settled individually,
in whole, with payments of XRP. Example: Alice incurs an obligation to
pay Bob an amount of XRP at some point in the future. Alice should only
be able to mark the obligation as paid if she can prove that the
required amount of XRP was paid to Bob via the Ripple ledger.

## Design

**The obligation contract**

I'll use the obligation contract I created of Ubin as a template but
will add an additional property called `settlementInstructions` of some
interface type.

Settlement can either be on-ledger or off-ledger. For on-ledger we can
specify cash states from which issuers are acceptable. For off-ledger,
there will be only one option for now: Ripple network settlement.
Different implementations can be used for each method.

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

The Ripple payment is actually made via a Corda flow which calls out
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

## Issues

1. Race between Ripple transaction being committed and the ledger number passing.







The create obligation flow should:
        * Figure out which Ripple ledger number is sensible to expect settlement by.
            * Flow will need access to this data.
        * Generate a UUID which will be used to track the Ripple transaction.
        * Specify which Ripple Oracle should be used to track settlement.
* Make the settlement payment by generating the tx specified above.
* Confirm settlement.




