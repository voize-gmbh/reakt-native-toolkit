package com.myrnproject.shared

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNotificationName


fun NSNotificationCenter.observerAsFlow(
    name: NSNotificationName,
    objectRef: Any? = null,
): Flow<NSNotification> = channelFlow {
    val observer = addObserverForName(
        name,
        objectRef,
        null,
    ) {
        if (it != null) {
            trySend(it)
        }
    }
    awaitClose {
        removeObserver(observer)
    }
}