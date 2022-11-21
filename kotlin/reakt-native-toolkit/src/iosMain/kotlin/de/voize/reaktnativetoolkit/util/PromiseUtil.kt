package de.voize.reaktnativetoolkit.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

typealias IOSResolvePromise<T> = (T) -> Unit
typealias IOSRejectPromise = (Throwable, Map<String, Any?>) -> Unit

data class PromiseIOS<T>(val resolve: IOSResolvePromise<T>, val reject: IOSRejectPromise)

inline fun <T> PromiseIOS<T>.launch(scope: CoroutineScope, crossinline block: suspend () -> T) {
    scope.launch {
        kotlin
            .runCatching { block() }
            .onFailure {
                reject(it, exceptionInterceptor(it))
            }
            .onSuccess { resolve(it) }
    }
}
