package net.corda.finance.obligation.serialization

//class DigitalCurrencySerializer : SerializationCustomSerializer<DigitalCurrency, DigitalCurrencySerializer.Proxy> {
//    data class Proxy(val currencyCode: String)
//
//    override fun toProxy(obj: DigitalCurrency) = Proxy(obj.currencyCode)
//
//    override fun fromProxy(proxy: Proxy): DigitalCurrency {
//        return DigitalCurrency.getInstance(proxy.currencyCode)
//    }
//}