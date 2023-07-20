package de.voize.reaktnativetoolkit.util

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface EventEmitter {
    val hasListeners: Flow<Boolean>
    fun sendEvent(eventName: String, params: Any?)
    fun sendEvent(eventName: String)
}

inline fun <reified T> EventEmitter.sendEventJson(eventName: String, params: T) {
    sendEvent(eventName, toReactNativeValueJson(params))
}

expect fun <T> toReactNativeValue(value: T): Any?

/**
 * Complex types are serialized to JSON. Primitive types are passed as-is.
 */
inline fun <reified T> toReactNativeValueJson(value: T): Any? {
    return when (value) {
        null, is String, is Boolean, is Number -> value
        else -> Json.encodeToString(value)
    }
}
