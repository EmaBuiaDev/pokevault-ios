package com.emabuia.pokevault.firebase

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Da `{"name":{"stringValue":"Pikachu"},"quantity":{"integerValue":"2"}}`,
 * come risponde la REST API di Firestore, a `{"name":"Pikachu","quantity":2}`,
 * che si decodifica direttamente nei modelli copiati dall'app Android.
 *
 * Le date diventano `{"seconds":..,"nanoseconds":..}`, la forma di
 * com.google.firebase.Timestamp che quei modelli si aspettano.
 */
fun firestoreFieldsToJson(fields: JsonObject): JsonObject =
    JsonObject(fields.mapValues { (_, value) -> firestoreValueToJson(value.jsonObject) })

@OptIn(ExperimentalTime::class)
fun firestoreValueToJson(value: JsonObject): JsonElement {
    val (kind, raw) = value.entries.firstOrNull() ?: return JsonNull
    return when (kind) {
        "stringValue", "referenceValue", "bytesValue" -> JsonPrimitive(raw.jsonPrimitive.content)
        // Gli interi arrivano come stringa: "integerValue":"2".
        "integerValue" -> JsonPrimitive(raw.jsonPrimitive.content.toLongOrNull() ?: 0L)
        "doubleValue" -> JsonPrimitive(raw.jsonPrimitive.content.toDoubleOrNull() ?: 0.0)
        "booleanValue" -> JsonPrimitive(raw.jsonPrimitive.booleanOrNull ?: false)
        "timestampValue" -> runCatching {
            val instant = Instant.parse(raw.jsonPrimitive.content)
            buildJsonObject {
                put("seconds", instant.epochSeconds)
                put("nanoseconds", instant.nanosecondsOfSecond)
            }
        }.getOrDefault(JsonNull)
        "arrayValue" -> JsonArray(
            raw.jsonObject["values"]?.jsonArray?.map { firestoreValueToJson(it.jsonObject) }.orEmpty()
        )
        "mapValue" -> firestoreFieldsToJson(raw.jsonObject["fields"]?.jsonObject ?: JsonObject(emptyMap()))
        else -> JsonNull // nullValue, geoPointValue: nei modelli portati non servono
    }
}
