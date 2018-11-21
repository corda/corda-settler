//package com.r3.corda.finance.obligation.app
//
//import javafx.collections.FXCollections
//import javafx.geometry.Insets
//import javafx.geometry.Pos
//import javafx.scene.control.Alert
//import javafx.stage.StageStyle
//import net.corda.client.jfx.utils.map
//import net.corda.client.jfx.utils.observeOnFXThread
//import net.corda.core.messaging.vaultTrackBy
//import net.corda.finance.GBP
//import net.corda.finance.USD
//import tornadofx.*
//import java.time.LocalDateTime
//import java.util.*
//
//class ObligationView : Fragment() {
//
//    val configFile: String by param()
//
//    var rippleClient: XRPClientForPayment
//    var controller: PollController
//
//    private val obligationsFeed = cordaRpcOps!!.vaultTrackBy<ObligationContract.State<*>>()
//    private val obligations = FXCollections.observableArrayList(obligationsFeed.snapshot.states)
//
//    init {
//        rippleClient = XRPClientForPayment(configFile)
//        controller = PollController {
//            val accountInfo = rippleClient.accountInfo(rippleClient.address)
//            DataResult(accountInfo.accountData.balance.toString(), LocalDateTime.now())
//        }
//        title = cordaRpcOps!!.nodeInfo().legalIdentities.first().name.organisation
//        controller.start()
//        obligationsFeed.updates.observeOnFXThread().subscribe {
//            println("THIS WAS EMITTED FROM THE NODE!!!")
//            println(it)
//            println("----------------------------------")
//            obligations.removeAll(it.consumed)
//            obligations.addAll(it.produced)
//            println("Updating...")
//        }
//    }
//
//    override val root = borderpane {
//        top {
//            hbox {
//                label {
//                    padding = Insets(10.0)
//                    text = "Ripple account balance:"
//                }
//                label {
//                    padding = Insets(10.0, 20.0, 10.0, 0.0)
//                    bind(controller.currentData)
//                }
//                label {
//                    padding = Insets(10.0, 0.0, 10.0, 0.0)
//                    text = "Address: "
//                }
//                vbox {
//                    alignment = Pos.CENTER
//                    textfield {
//                        text = rippleClient.address.toString()
//                        minWidth = 190.0
//                    }
//                }
//            }
//        }
//        minWidth = 700.0
//        style { fontSize = 8.px }
//        center {
//            tableview<ObligationModel<*>> {
//                items = obligations.map { it.state.data }.transform { it.toUiModel() }
//                columnResizePolicy = SmartResize.POLICY
//                readonlyColumn("ObligationContract ID", ObligationModel<*>::linearId)
//                readonlyColumn("Currency", ObligationModel<*>::amount).cellFormat {
//                    text = (it.token as? DigitalCurrency)?.currencyCode ?: (it.token as Currency).currencyCode
//                }
//                readonlyColumn("Amount", ObligationModel<*>::amount).cellFormat {
//                    text = when (it.token) {
//                        Ripple -> {
//                            // Hack... Smallest Ripple faceAmount is 10^-6.
//                            (it.quantity / 1000000).toString()
//                        }
//                        USD, GBP -> it.quantity.toString()
//                        else -> throw IllegalStateException("Unrecognised currency.")
//                    }
//
//                }
//                readonlyColumn("Counterparty", ObligationModel<*>::counterparty).cellFormat {
//                    text = PartyNameFormatter.short.format(it.name)
//                }
//                readonlyColumn("Role", ObligationModel<*>::isPayer).cellFormat {
//                    text = if (it) "Obligor" else "Obligee"
//                }
//                readonlyColumn("Action / Status", ObligationModel<*>::hasSettlementInstructions).cellFormat { hasSettlementInstructions ->
//                    graphic = null
//                    text = ""
//                    val isBeneficiary = !rowItem.isPayer
//                    when {
//                        isBeneficiary && !hasSettlementInstructions -> {
//                            graphic = hbox {
//                                style { fontSize = 8.px }
//                                button("Add settlement instructions").action {
//                                    find<AddSettlementInstructions>().apply {
//                                        linearId.set(focusModel.focusedItem.linearId)
//                                        openModal(stageStyle = StageStyle.UTILITY)
//                                    }
//                                }
//                            }
//                        }
//                        isBeneficiary && hasSettlementInstructions -> {
//                            text = "Settlement instructions added. Waiting for payment..."
//                        }
//                        !isBeneficiary && hasSettlementInstructions -> {
//                            graphic = hbox {
//                                button("Make payment").action {
//                                    val haveRequiredFunds = checkBalance(rippleClient, rowItem)
//                                    if (!haveRequiredFunds) {
//                                        alert(Alert.AlertType.ERROR, "You don't have enough funds to make the payment!")
//                                    } else {
//                                        // TODO: Check whether payment has been paid or not before proceeding.
//                                        val transactionHash = makePayment(rippleClient, rowItem)
//                                        val flow = cordaRpcOps!!.startFlowDynamic(UpdateObligationWithPaymentRef::class.java, rowItem.linearId, transactionHash)
//                                        val flowResult = flow.returnValue.getOrThrow()
//                                        val flowTwo = cordaRpcOps!!.startFlowDynamic(SendToSettlementOracle::class.java, rowItem.linearId)
//                                        val resultTwo = flowTwo.returnValue.getOrThrow()
//                                        println(resultTwo)
//                                        val newObligations = cordaRpcOps!!.vaultTrackBy<ObligationContract.State<*>>()
//                                        obligations.clear()
//                                        obligations.addAll(newObligations.snapshot.states)
//                                    }
//                                }
//                            }
//                        }
//                        !isBeneficiary && !hasSettlementInstructions -> {
//                            text = "Waiting for obligee to add settlement instructions..."
//                        }
//                    }
//                }
//            }
//        }
//        bottom {
//            toolbar {
//                button("Add obligation").action {
//                    find<AddObligationView>().openModal(stageStyle = StageStyle.UTILITY)
//                }
//            }
//        }
//    }
//}