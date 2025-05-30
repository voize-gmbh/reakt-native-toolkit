package com.myrnproject.shared

import de.voize.reaktnativetoolkit.annotation.*
import de.voize.reaktnativetoolkit.util.EventEmitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.seconds

val intervalFlow = flow {
    while (currentCoroutineContext().isActive) {
        emit("Hello from Kotlin!")
        delay(1.seconds)
    }
}

@ReactNativeModule("NotificationDemo", supportedEvents = ["notification"])
class NotificationDemo(
        private val eventEmitter: EventEmitter,
        lifecycleScope: CoroutineScope,
) {
    @ReactNativeEvent("notification")
    fun notifications(): Flow<String> = intervalFlow
}

