package tv.enktel.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Lenient JSON helpers — Xtream panels return numbers as strings, nulls, empty arrays, etc. */
val LenientJson = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

fun JsonElement?.obj(): JsonObject? = this as? JsonObject
fun JsonElement?.arr(): JsonArray? = this as? JsonArray
fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)?.takeIf { it !is JsonNull }

fun JsonElement?.str(key: String): String? =
    (get(key) as? JsonPrimitive)?.contentOrNullSafe()?.takeIf { it.isNotBlank() }

fun JsonElement?.long(key: String): Long? = str(key)?.toDoubleOrNull()?.toLong()
fun JsonElement?.int(key: String): Int? = long(key)?.toInt()
fun JsonElement?.double(key: String): Double? = str(key)?.toDoubleOrNull()
fun JsonElement?.bool(key: String): Boolean {
    val v = str(key) ?: return false
    return v == "1" || v.equals("true", true)
}

private fun JsonPrimitive.contentOrNullSafe(): String? = if (this is JsonNull) null else content
