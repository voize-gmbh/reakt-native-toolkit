package de.voize.reaktnativetoolkit.util

import kotlinx.coroutines.flow.StateFlow

class EventEmitterImpl(
    private val sendEventFunction: (name: String, body: Any?) -> Unit,
    override val hasListeners: StateFlow<Boolean>
) : EventEmitter {
    override fun <T> sendEvent(eventName: String, params: T) {
        sendEventFunction(eventName, params)
    }

    override fun sendEvent(eventName: String) {
        sendEventFunction(eventName, emptyMap<String, String>())
    }
}