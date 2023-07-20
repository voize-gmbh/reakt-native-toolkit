package de.voize.reaktnativetoolkit.util

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

inline fun <reified T> Promise.runCatchingWithArguments(action: () -> T, argumentsTransform: (T) -> Any?) {
    kotlin.runCatching {
        when (val result = action()) {
            is Unit -> null
            else -> argumentsTransform(result)
        }
    }.onSuccess(::resolve).onFailure {
        reject(null, it.allMessages(), it, Arguments.makeNativeMap(exceptionInterceptor(it)))
    }
}

inline fun <reified T> Promise.launch(scope: CoroutineScope, crossinline action: suspend () -> T) {
    scope.launch {
        runCatchingWithArguments({ action() }, ::toReactNativeValue)
    }
}

inline fun <reified T> Promise.launchJson(scope: CoroutineScope, crossinline action: suspend () -> T) {
    scope.launch {
        runCatchingWithArguments({ action() }, ::toReactNativeValueJson)
    }
}
