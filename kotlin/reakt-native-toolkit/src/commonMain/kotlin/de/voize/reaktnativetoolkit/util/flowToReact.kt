package de.voize.reaktnativetoolkit.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.serializersModuleOf
import kotlin.time.Duration.Companion.minutes

private class DynamicLookupSerializer : KSerializer<Any> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Any")

    override fun serialize(encoder: Encoder, value: Any) {
        val jsonEncoder = encoder as JsonEncoder
        val jsonElement = serializeAny(value)
        jsonEncoder.encodeJsonElement(jsonElement)
    }

    private fun serializeAny(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is Map<*, *> -> {
            val mapContents = value.entries.associate { mapEntry ->
                mapEntry.key.toString() to serializeAny(mapEntry.value)
            }
            JsonObject(mapContents)
        }

        is List<*> -> {
            val arrayContents = value.map { listEntry -> serializeAny(listEntry) }
            JsonArray(arrayContents)
        }

        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is String -> JsonPrimitive(value)
        else -> error("Unsupported type ${value::class}")
    }

    override fun deserialize(decoder: Decoder): Any {
        error("Unsupported")
    }
}

private val flowToReactJson = Json {
    serializersModule = serializersModuleOf(DynamicLookupSerializer())
    encodeDefaults = true
}

private val subscriptions = MutableStateFlow(emptyMap<String, String>())

internal class ReactNativeUseFlowSubscriptionCancellationException : CancellationException("useFlow subscription cancelled")

suspend inline fun <reified T> Flow<T>.toReact(subscriptionId: String, previous: String?): String {
   return toReact(subscriptionId, previous, serializer<T>())
}

suspend fun <T> Flow<T>.toReact(subscriptionId: String, previous: String?, serializer: SerializationStrategy<T>): String {
    val invocationId = uuid()
    try {
        subscriptions.update {
            it + (subscriptionId to invocationId)
        }
        return coroutineScope {
            val next = async {
                map { flowToReactJson.encodeToString(serializer, it) }.first { it != previous }
            }
            val checkActiveInvocation = launch {
                subscriptions.first { it[subscriptionId] != invocationId }
                next.cancel(ReactNativeUseFlowSubscriptionCancellationException())
            }
            try {
                withTimeoutOrNull(10.minutes) {
                    next.await()
                } ?: previous ?: error(
                    "Flow did not emit a value within 10 minutes.\n" +
                            "This indicates @ReactNativeFlow was used on a flow that does not represent a state."
                )
            } finally {
                checkActiveInvocation.cancel()
            }
        }
    } finally {
        subscriptions.update { if (it[subscriptionId] == invocationId) it - subscriptionId else it }
    }
}

fun unsubscribeFromFlow(subscriptionId: String) {
    subscriptions.update { it - subscriptionId }
}
