package com.r3.corda.lib.settler.ripple.utilities

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.r3.corda.lib.settler.ripple.types.ResultObject

val mapper = ObjectMapper().registerKotlinModule()

inline fun <reified T : ResultObject> deserialize(response: String): T {
    return mapper.readValue(response, T::class.java)
}