package de.voize.reaktnativetoolkit.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.serializersModuleOf

private class DynamicLookupSerializer: KSerializer<Any> {
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

val flowToReactJson = Json {
    serializersModule = serializersModuleOf(DynamicLookupSerializer())
    encodeDefaults = true
}

suspend inline fun <reified T> Flow<T>.toReact(previous: String?): String {
    return map { flowToReactJson.encodeToString(it) }.first { it != previous }
}
