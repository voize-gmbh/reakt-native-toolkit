package de.voize.reaktnativetoolkit.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import react_native.*

class EventEmitterIOS(
    private val getCallableJSModule: () -> RCTCallableJSModules?,
    private val supportedEvents: List<String>,
) : EventEmitter {
    private val listenersCount = MutableStateFlow(0)
    override val hasListeners = listenersCount.map { it > 0 }

    private fun sendEventFunction(eventName: String, body: Any?) {
        val _callableJSModule =
            getCallableJSModule() ?: throw IllegalStateException("No callableJSModule set")

        if (eventName !in supportedEvents) {
            throw IllegalArgumentException("Event $eventName is not supported")
        }

        _callableJSModule.invokeModule(
            "RCTDeviceEventEmitter",
            "emit",
            buildList {
                add(eventName)
                if (body != null) {
                    add(body)
                }
            }
        )
    }

    override fun sendEvent(eventName: String, params: Any?) {
        sendEventFunction(eventName, params)
    }

    override fun sendEvent(eventName: String) {
        sendEventFunction(eventName, emptyMap<String, String>())
    }

    fun addListener(eventName: String) {
        if (eventName !in supportedEvents) {
            throw IllegalArgumentException("Event $eventName is not supported")
        }

        listenersCount.update { it + 1 }
    }

    fun removeListeners(count: Double) {
        listenersCount.update { it - count.toInt() }
    }

}

actual fun <T> toReactNativeValue(value: T): Any? {
    return when (value) {
        null, is String, is Boolean, is Number, is List<*>, is Map<*, *> -> value
        else -> throw IllegalArgumentException("Unsupported value type ${value!!::class.simpleName}")
    }
}

