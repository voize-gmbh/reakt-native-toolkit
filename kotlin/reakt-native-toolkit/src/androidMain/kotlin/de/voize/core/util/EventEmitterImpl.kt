package de.voize.core.util

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.modules.core.DeviceEventManagerModule
import kotlinx.coroutines.flow.Flow

class EventEmitterImpl(
    private val reactContext: ReactApplicationContext,
    override val hasListeners: Flow<Boolean>
) : EventEmitter {

    private val jsModule by lazy {
        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
    }

    override fun <T> sendEvent(eventName: String, params: T) {
        jsModule.emit(eventName, params.toArguments())
    }

    override fun sendEvent(eventName: String) {
        sendEvent(eventName, null)
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
