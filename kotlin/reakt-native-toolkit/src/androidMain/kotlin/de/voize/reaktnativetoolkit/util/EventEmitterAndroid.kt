package de.voize.reaktnativetoolkit.util

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.modules.core.DeviceEventManagerModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class EventEmitterAndroid(
    private val reactContext: ReactApplicationContext,
    private val supportedEvents: List<String>,
) : EventEmitter {

    private val listenersCount = MutableStateFlow(0)
    override val hasListeners = listenersCount.map { it > 0 }

    private val jsModule by lazy {
        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
    }

    override fun <T> sendEvent(eventName: String, params: T) {
        jsModule.emit(eventName, params.toArguments())
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

fun <T> T.toArguments(): Any? {
    return when (this) {
        null, is String, is Boolean, is Number -> this
        is List<*> -> Arguments.makeNativeArray(this.map { it.toArguments() })
        is Map<*, *> -> Arguments.makeNativeMap(this.mapValues { it.value.toArguments() } as Map<String, Any>)
        else -> throw IllegalArgumentException("Unsupported value type ${this!!::class.java}")
    }
}
