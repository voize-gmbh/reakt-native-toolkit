package de.voize.reaktnativetoolkit.util

import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

abstract class ReactNativeModuleBase(
    reactContext: ReactApplicationContext,
    supportedEvents: List<String>,
) : ReactContextBaseJavaModule(reactContext) {
    protected val eventEmitter = EventEmitterAndroid(reactContext, supportedEvents)
}