package ai.sotuvchi.app.core.network

import java.time.Instant
import java.time.OffsetDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.doubleOrNull

object ApiJson {
    /** Unknown keys are ignored; nulls for non-null fields fall back to defaults. */
    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = false
    }
}

/** Dates are ISO 8601 UTC strings, with or without fractional seconds. */
fun parseIsoInstant(value: String): Instant? =
    runCatching { Instant.parse(value) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()

object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Instant {
        val raw = decoder.decodeString()
        return parseIsoInstant(raw) ?: throw IllegalArgumentException("Invalid ISO 8601 date: $raw")
    }

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
}

/** A JSON number that may arrive as an integer, a float or a numeric string. */
object LooseIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LooseInt", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int {
        val element = (decoder as? JsonDecoder)?.decodeJsonElement()
            ?: return decoder.decodeInt()
        return looseInt(element) ?: throw IllegalArgumentException("Not a number")
    }

    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}

fun looseInt(element: JsonElement?): Int? {
    val primitive = element as? JsonPrimitive ?: return null
    if (primitive is JsonNull) return null
    primitive.intOrNull?.let { return it }
    primitive.doubleOrNull?.let { return Math.round(it).toInt() }
    return primitive.content.trim().toIntOrNull()
}

/**
 * Decodes enums leniently: values added on the server later become `unknown`
 * instead of failing the whole response.
 */
abstract class LenientEnumSerializer<E : Enum<E>>(
    name: String,
    private val values: List<E>,
    private val unknown: E,
    private val raw: (E) -> String,
) : KSerializer<E> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(name, PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): E {
        val value = decoder.decodeString()
        return values.firstOrNull { raw(it) == value } ?: unknown
    }

    override fun serialize(encoder: Encoder, value: E) = encoder.encodeString(raw(value))
}

/** Every endpoint but billing answers `{ success, data }` or `{ success, error }`. */
@Serializable
data class Envelope<T>(
    val success: Boolean? = null,
    val data: T? = null,
    val error: String? = null,
)

@Serializable
data class ErrorBody(val error: String? = null)
