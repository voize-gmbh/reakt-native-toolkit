package de.voize.reaktnativetoolkit.util

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.coroutines.CoroutineContext

abstract class ReactNativeModuleBase(
    reactContext: ReactApplicationContext,
) : ReactContextBaseJavaModule(reactContext) {
    protected val listenerCount = MutableStateFlow(0)
    protected val eventEmitter = EventEmitterImpl(reactContext, listenerCount.map { it > 0 })
}