package de.voize.reaktnativetoolkit.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

typealias IOSResolvePromise<T> = (T) -> Unit
typealias IOSRejectPromise = (Throwable, Map<String, Any?>) -> Unit

data class PromiseIOS(val resolve: IOSResolvePromise<Any?>, val reject: IOSRejectPromise)

inline fun <reified T> PromiseIOS.runCatchingWithArguments(action: () -> T, argumentsTransform: (T) -> Any?) {
    kotlin
        .runCatching { argumentsTransform(action()) }
        .onSuccess(resolve)
        .onFailure {
            reject(it, exceptionInterceptor(it))
        }
}

inline fun <reified T> PromiseIOS.launch(scope: CoroutineScope, crossinline action: suspend () -> T) {
    scope.launch {
        runCatchingWithArguments({ action() }, ::toReactNativeValue)
    }
}

inline fun <reified T> PromiseIOS.launchJson(scope: CoroutineScope, crossinline action: suspend () -> T) {
    scope.launch {
        runCatchingWithArguments({ action() }, ::toReactNativeValueJson)
    }
}
