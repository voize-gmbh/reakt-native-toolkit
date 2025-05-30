package de.voize.reaktnativetoolkit.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.collect

fun <T> Flow<T>.toEventEmitter(
    eventEmitter: EventEmitter,
    eventName: String,
    scope: CoroutineScope,
) {
    eventEmitter.hasListeners.transformLatest { hasListeners ->
        if (hasListeners) {
            collect { value ->
                eventEmitter.sendEventJson(eventName, value)
            }
        }
    }.launchIn(scope)
}
