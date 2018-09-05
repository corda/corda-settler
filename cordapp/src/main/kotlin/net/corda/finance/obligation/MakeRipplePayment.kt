package net.corda.finance.obligation

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowLogic
import net.corda.finance.obligation.contracts.Obligation

class MakeRipplePayment(
        val obligationStateAndRef: StateAndRef<Obligation.State<*>>
) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {

    }

}


//data class DefaultFlowHttpRequest(
//        override val uri: URI,
//        override val method: String,
//        override val contentType: String,
//        override val body: String,
//        override val timeout: Int
//) : FlowHttpRequest {
//    override fun execute(): FlowHttpResponse {
//        val type = contentType
//        return uri.toURL().openHttpConnection().run {
//            doInput = true
//            doOutput = true
//            requestMethod = method
//            connectTimeout = timeout
//            setRequestProperty("Content-Type", type)
//            OutputStreamWriter(outputStream).use { out -> out.write(body) }
//            checkOkResponse()
//            FlowHttpResponse(responseCode, responseMessage, inputStream.reader().readText())
//        }
//    }
//}