# Corda settler

## Background

Obligations which are represented on a Corda ledger can be settled individually,
in whole or in part, with Corda Settler. Example: Alice
incurs an obligation to pay Bob an amount of USD at some point in the
future. Alice should only be able to mark the obligation as paid if she
can prove that the required amount payment was made to Bob via the specified method

This repository contains an implementation of the Corda Settler with a
plugin to handle off-ledger settlement in other non-Corda payment systems.

## Usage

Ensure you have Maven and Android Platform/API level 10 (2.3.3 APIs) installed.

* A guide to installing Maven can be found [here](https://www.baeldung.com/install-maven-on-windows-linux-mac)
* Download [Android Studio](https://developer.android.com/studio/) or the [Android CLI](https://developer.android.com/studio/command-line/) in order to install 2.3.3 APIs.

Clone and locally install the Ripple Java Library:

    git clone https://github.com/ripple-unmaintained/ripple-lib-java
    cd ripple-lib-java
    mvn install

Clone the Corda Settler repository and deploy locally:

    git clone http://github.com/corda/corda-settler
    cd corda-settler
    ./gradlew clean deployNodes

**Note**: In the OffLedgerSettleObligation flow, XRP will be sent from the account specified in the xrp.conf file located in cordapp/src/main/resources. You will need to rerun the deploy command when you change this information.

Run the nodes:

    cd build/nodes
    ./runnodes

You should see four nodes open in your terminal.

Start with `Party A` and paste the following command to create a new
obligation:

    start CreateObligation amount: { quantity: 1000, token: { currencyCode: USD, type: fiat } }, role: OBLIGOR, counterparty: PartyB, dueBy: 1543922400, anonymous: false

If the flow fails due to `☠   Due by date must be in the future.` then
increase the value of the timestamp to a date in the future!

The node shell will output the result of the flow which should print the
details of the new obligation that looks something like this:

    OUTPUT:     Obligation(d6f9bb92-c903-4c54-9121-97a2b3afb1b2): PartyA owes PartyB 10.00 USD (0.00 USD paid).
                Settlement status: UNSETTLED
                SettlementMethod: No settlement method added
                Payments:
                    No payments made.
    COMMAND:    com.r3.corda.finance.obligation.commands.ObligationCommands.Create with pubkeys DL4AeA53y7qHJDEQrEJYiEsycihxhz1uNEoc5jEFvuyAt9, DLDnLmKJ5kfJm2qNv3NpbD8QD9dcZGNm2YXXXvptrLmcdg
    ATTACHMENT: BE850C17C89B5B55B1962AEC78947404A36EC05FD8FA1AE52207EEB052F8B977

From the output, copy the UUID for the obligation which was output
on the first line `OUTPUT:     Obligation(UUID)`.

Next, from the `Party A` node, novate the obligation face value token to XRP:

    start NovateObligation linearId: PASTE_UUID, novationCommand: { oldToken: { currencyCode: USD, type: fiat }, newToken: { currencyCode: XRP, type: digital }, oracle: Oracle, type: token }

Next, from the `Party B` node, we need to add the settlement instructions.

## Using XRP as the settlement rail
You will need to use an XRP address of an XRP account which you control. If
you don't have an XRP account then you can get one from the testnet
Faucet: https://developers.ripple.com/xrp-test-net-faucet.html. This account 
should be different to the one that the XRP is being sent from, defined in 
`xrp.conf`.

    start UpdateSettlementMethod linearId: PASTE_UUID, settlementMethod: { accountToPay: PASTE_ACCOUNT, settlementOracle: Oracle, _type: com.r3.corda.finance.ripple.types.XrpSettlement }

Lastly, we want to settle the obligation with a payment of XRP. We can do
this with the following command:

    start OffLedgerSettleObligation amount: { quantity: 20000000, token: { currencyCode: XRP, type: digital } }, linearId: PASTE_UUID

You should see that the obligation is now settled. You can inspect the XRP
ledger using the payment reference for the payment which is noted in the
output for this command. Although there is support for a real exchange rate
Oracle in this repository, the demo uses a fixed exchange rate of XRP/USD 0.50.

## Repo structure

There are five modules in this repo:

1. `cordapp-states-contracts` which contains the `Obligation` state and
   `ObligationContract`, as well as some abstract flows  definitions and
   types for obligation payments and settlement methods. This module also
   contains some token type definitions which will eventually be refactored
   out of this repository when the Corda Token SDK binaries are available.
   IMPORTANT: This module does not depend on the `Ripple` module. This module
   makes no assumption about the nature of the settlement rail.
2. `cordapp` which contains the flows for issuing, cancelling, novating and
   selling obligations. The main flow of interest in this module is the
   `MakeOffLedgerPayment` flow which is abstract. The expectation is that
   CorDapp developers will sub-class this with their own flows for specific off-ledger
   payment methods. You'll see that the flow defines a bunch of abstract
   methods for checking balances, making payments and setting up the
   process. Currently there is one implementation of this flow in the `ripple`
   module.
3. `ripple` which contains an implementation of the `MakeOffLedgerPayment` flow
   called `MakeXrpPayment`. `MakeXrpPayment` uses the `ripple-lib-java`
   library to create and sign XRP transactions. The remainder of this module
   defines the types, serialisers and client interface necessary for
   interacting with Ripple nodes. The interfaces are defined in the `services`
   package.
4. `swift` which contains an implementation of the `MakeOffLedgerPayment` flow
   called `MakeSWIFTPayment`. `MakeSWIFTPayment` uses SWIFT http APIs
   to submit SWIFT payment instruction. 
5. `oracle` which contains an implementation of the `XrpOracleService` which
   checks whether an XRP payment specified by a transaction hash has
   credited a specific XRP account. There is also a stubbed-out exchange rate
   Oracle which is required for novating obligation face value token types.

## Implementing your own settler

1. Add a new module to this project with an outline similar to the `ripple`
   module.
2. The settlement rail you intend to use probably already has a Java client
   API, so all you need to do is create a wrapper around this for Corda.
   Look at what I did with the `Ripple` library as an example. If you are
   sending library types over the wire, you'll need to create proxy
   serialisers for those types. The interface to your payment rail should
   exist as a `CordaService`.
3. Sub-class `MakeOffLedgerPayment` for creating a payment using the
   payment rail of your choice. Note, that the payment must also be
   submitted to the payment rail. For Ripple and other cryptos this is easy
   as there are publicly available nodes. For legacy rails like RTGS and DFS
   you'll need access to an API for submitting transactions.
4. Implement an Oracle service which will update and sign a transaction
   containing a payment against an Oracle service, if and only if the
   payment credited the specified beneficiaries account on the settlement
   rail. For cryptos you can query some nodes of your choosing to check
   whether the payment settled correctly. For RTGS and DFS, again, you'll
   need access to an API.
5. Add a `SettlementMethod` type for your payment rail.
6. Add a `Payment` type for your payment rail.

If you get stuck then e-mail roger [dot] willis [@] r3 [dot] com for help!

## Payment rail operators wanting to integrate with the Corda Settler

At a high level you need the following:

1. Provide an API for submitting payment instructions. The API should
   return the ID of the payment transaction. This probably already exists.
2. Provide an API for checking the status of the payment. The API should
   only return "SUCCESS" if and only if the payment credits the specified
   beneficiaries account. This will likely need adding to your API.

E-mail roger [dot] willis [@] r3 [dot] com for more information.

## Design

**The obligation contract**

As at 4/12/18.

This repo uses the obligation contract created for project Ubin with a
couple of differences/additions. For example, there are new properties called
`settlementmethod` and `payments`, both of an interface type.

Settlement can either be on-ledger or off-ledger. For on-ledger we can
specify token states from which issuers are acceptable. Note, that this
is not implemented in this project. See the [obligation cordapp](https://github.com/corda/obligation-cordapp)
for how this is implemented. For off-ledger, there will be only
one option for now: XRP settlement. Different implementations can be
used for each method. The settler CorDapp is settlement rail agnostic!
To add support for more rails just add a CorDapp that sub-classes the
payment flow and adds settlement instructions for that settlement type.

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
problem". As such, we will need to add the ledger number of the XRP
ledger which we expect payment to be made by. This can be added after
the payment instruction has been sent to a Ripple node.

In terms of settlement instructions for off-ledger XRP payments, we'll
need:

* which XRP address Bob expects Alice to make a payment to
* which Ripple ledger number the payment should be expected by - this is
  effectively the deadline
* a UUID to track the Ripple payment - this should be the hash of the
  linear ID of the obligation the payment will (partially) extinguish
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

At this point, both parties are in consensus that an XRP payment is
owing, precisely how it should be made and what evidence the oracle will
require from the Ripple ledger in order to be prepared to sign a
statement that the payment has indeed been made.

**Step 3. Making the XRP payment.**

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

At this point, the obligation is updated with the information for the
XRP payment which has just been made. The following details are required:

* `amount` as the payment may only partially settle the obligation
* `lastLedgerSequence`
* `paymentReference`
* `status`, which is defaulted to `SENT`

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

**Step 4. Extinguishing the obligation.**

The obligation can be submitted to the Oracle at any point after the
Ripple transaction hash has been added to obligation.

The Oracle will signer over an XRP payment which (partially) settles an
obligation, if and only if:

* transactionResult: tesSUCCESS and validated: true
* the account specified in the settlement instructions as the recipient
* there is a UUID in the transaction memo field, equal to the UUID
  specified in the settlement method

The server_state of the rippled process used by the Oracle must be
set to "tracking", "full", "validating", or "proposing".

Corda transaction proposals can be sent to the Oracle at any time for
signing. The Oracle is configured to cache transaction proposals until
the specified Ripple transaction is

a) included in a validated Ripple ledger instance, or;
b) the last permissible Ripple ledger has been closed.

As such, if the the Ripple ledger_index exceeds the specified
LastLedgerSequence then the Oracle will return an exception for the
given transaction hash, indicating that the window for committing the
transaction has passed.

At this point, a new settlement method can be added, if necessary. XRP
payments might fail if they are paid to the wrong address or if the
invoiceID is not set to the correct value. IF this is the case, then
such issues must be resolved manually for now.

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

* The Ripple account secret and cryptocompare API key are included in
  platin text intentionally as it makes the repo easier to use. The ripple
  account is a testnet account. And the Cryptocompare API is free.
* We use the Ripple ledger_index to specify when the payment should be
  made by. Note that each ledger number takes about 3-5 seconds. The Oracle
  currently waits up to 60 seconds for a payment to settle. This is more
  than enough time!
* The unit tests require Internet connectivity, specifically, you need to
  be able to access the Ripple testnet node and the exchange rate
  provider.
* The Unit tests require you to use an XRP account that has enough XRP
  to make the payments. If your specified account in `xrp.conf` has ran
  out of XRP then get a new one [here](https://developers.ripple.com/xrp-test-net-faucet.html).
* For now, the unit tests require that you have an account with
  http://cryptocompare.com to get the XRP/USD exchange rate. NOTE: There
  is no affiliation to this provider, it was just the first one I found
  that worked easily! Feel free to replace it with your own provider. The
  exchange rate oracle for this project is really just a stub so we can
  handle token novation.
* SWIFT tests require a correct API key to be provided (look for `EMAIL IVAN/ROGER FOR API KEY`
  in the codebase).
* SWIFT tests expect a file `swiftKey.pem` with a private key on the classpath. 

## TODO

* Need to re-implement this with the human computer interaction API when
  it is available as currently, parties auto accept any change to obligations,
  instead, we want to give them the opportunity to assent to a change,
  provide a counter-change or just reject the change entirely.
* The Settlement method contains a `Class<T>` which is the flow that is
  run for off ledger settlement. Clearly, there are some security implications
  here. Currently `T` is restricted to sub-classes of `MakeOffLedgerPayment`
  but we should add a white-list of accepted off ledger payment flows as
  well.
* The Ripple secret should not be stored in plaintext and in a config file!
  Define some flows for adding and getting this config information.
* Define a settlement Oracle interface/abstract class and move reusable
  parts of the `XrpOracleService` to the abstract class.
* Add the option to use a mock Fx Oracle that just provides a single
  fixed Fx rate.
* Make readme agnostic to payment method



