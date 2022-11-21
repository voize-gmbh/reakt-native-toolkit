package de.voize.reaktnativetoolkit.util

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

inline fun <T> Promise.runCatching(action: () -> T) {
    kotlin.runCatching {
        when (val result = action()) {
            is Unit -> null
            else -> result.toArguments()
        }
    }.onSuccess { resolve(it) }.onFailure {
        reject(null, it.allMessages(), it, Arguments.makeNativeMap(exceptionInterceptor(it)))
    }
}

inline fun <T> Promise.launch(scope: CoroutineScope, crossinline action: suspend () -> T) {
    scope.launch {
        this@launch.runCatching { action() }
    }
}