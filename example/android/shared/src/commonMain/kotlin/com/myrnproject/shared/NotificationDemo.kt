package com.myrnproject.shared

import de.voize.reaktnativetoolkit.annotation.*
import de.voize.reaktnativetoolkit.util.EventEmitter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.seconds

val intervalFlow = flow {
    while (currentCoroutineContext().isActive) {
        emit(Unit)
        delay(1.seconds)
    }
}

@ReactNativeModule("NotificationDemo", supportedEvents = ["notification"])
class NotificationDemo(
        private val eventEmitter: EventEmitter,
        lifecycleScope: CoroutineScope,
) {
    init {
        eventEmitter.hasListeners.transformLatest<Boolean, Unit> { hasListeners ->
            if (hasListeners) {
                intervalFlow.collect {
                    eventEmitter.sendEvent("notification", "Hello from Kotlin!")
                }
            }
        }.launchIn(lifecycleScope)
    }
}