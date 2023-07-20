package de.voize.reaktnativetoolkit.util

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.modules.core.DeviceEventManagerModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class EventEmitterAndroid(
    private val reactContext: ReactApplicationContext,
    private val supportedEvents: List<String>,
) : EventEmitter {

    private val listenersCount = MutableStateFlow(0)
    override val hasListeners = listenersCount.map { it > 0 }

    val jsModule by lazy {
        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
    }
     override fun sendEvent(eventName: String, params: Any?) {
        jsModule.emit(eventName, params)
    }

    override fun sendEvent(eventName: String) {
        sendEvent(eventName, null)
    }

    fun addListener(eventName: String) {
        if (eventName !in supportedEvents) {
            throw IllegalArgumentException("Event $eventName is not supported")
        }

        listenersCount.update { it + 1 }
    }

    fun removeListeners(count: Int) {
        listenersCount.update { it - count }
    }
}

actual fun <T> toReactNativeValue(value: T): Any? {
    return when (value) {
        null, is String, is Boolean, is Number -> value
        is List<*> -> Arguments.makeNativeArray(value.map { toReactNativeValue(it) })
        is Map<*, *> -> Arguments.makeNativeMap(value.mapValues { toReactNativeValue(it.value) } as Map<String, Any?>)
        else -> throw IllegalArgumentException("Unsupported value type ${value!!::class.java}")
    }
}
