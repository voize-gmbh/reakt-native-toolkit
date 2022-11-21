package de.voize.reaktnativetoolkit.util

import kotlinx.coroutines.flow.Flow

interface EventEmitter {
    val hasListeners: Flow<Boolean>
    fun <T> sendEvent(eventName: String, params: T)
    fun sendEvent(eventName: String)
}