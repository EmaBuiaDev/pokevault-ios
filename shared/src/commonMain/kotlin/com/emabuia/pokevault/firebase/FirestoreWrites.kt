package com.emabuia.pokevault.firebase

import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import com.emabuia.pokevault.data.model.Timestamp
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Il valore "adesso" per un campo data: com.google.firebase.Timestamp.now() su Android. */
object ServerNow

/**
 * Le scritture sulla collezione via REST: le stesse che FirestoreRepository
 * fa con l'SDK su Android, sugli stessi documenti e con gli stessi campi.
 *
 * Valgono le regole di sicurezza del progetto (ognuno scrive solo sotto
 * users/{suo uid}), esattamente come per le scritture dell'app Android.
 */
class FirestoreWrites(
    private val client: HttpClient,
    private val projectId: String = FirebaseConfig.PROJECT_ID,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val database get() = "projects/$projectId/databases/(default)"
    private val api get() = "https://firestore.googleapis.com/v1/$database/documents"

    private fun cardPath(uid: String, cardId: String) = "$database/documents/users/$uid/cards/$cardId"
    private fun userPath(uid: String) = "$database/documents/users/$uid"

    /**
     * Le carte con quell'apiCardId e quella stampa: la stessa query
     * whereEqualTo("apiCardId").whereEqualTo("variant") di addCard su Android.
     */
    suspend fun findPrints(uid: String, idToken: String, apiCardId: String, variant: String? = null): List<Pair<String, JsonObject>> {
        val filters = buildJsonArray {
            add(equalFilter("apiCardId", apiCardId))
            if (variant != null) add(equalFilter("variant", variant))
        }
        val where = if (filters.size == 1) filters[0] else buildJsonObject {
            putJsonObject("compositeFilter") {
                put("op", "AND")
                put("filters", filters)
            }
        }
        val body = buildJsonObject {
            putJsonObject("structuredQuery") {
                putJsonArray("from") { add(buildJsonObject { put("collectionId", "cards") }) }
                put("where", where)
            }
        }
        val response = client.post("$api/users/$uid:runQuery") {
            expectSuccess = false
            bearerAuth(idToken)
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        val text = response.bodyAsText()
        check(response.status.isSuccess()) { "Firestore ${response.status.value}: $text" }
        return json.parseToJsonElement(text).jsonArray.mapNotNull { row ->
            val document = row.jsonObject["document"]?.jsonObject ?: return@mapNotNull null
            val id = document["name"]?.jsonPrimitive?.content?.substringAfterLast('/') ?: return@mapNotNull null
            id to firestoreFieldsToJson(document["fields"]?.jsonObject ?: JsonObject(emptyMap()))
        }
    }

    /** Un documento carta nuovo, con l'id generato qui come fa l'SDK. Restituisce l'id. */
    suspend fun createCard(uid: String, idToken: String, fields: Map<String, Any?>): String {
        val cardId = newDocumentId()
        commit(idToken, buildJsonObject {
            putJsonObject("update") {
                put("name", cardPath(uid, cardId))
                put("fields", toFields(fields))
            }
            // Mai sovrascrivere un documento esistente per errore.
            putJsonObject("currentDocument") { put("exists", false) }
        })
        return cardId
    }

    /** Aggiorna solo i campi dati, come docRef.update(map) su Android. */
    suspend fun updateCard(uid: String, idToken: String, cardId: String, fields: Map<String, Any?>) {
        commit(idToken, buildJsonObject {
            putJsonObject("update") {
                put("name", cardPath(uid, cardId))
                put("fields", toFields(fields))
            }
            putJsonObject("updateMask") { putJsonArray("fieldPaths") { fields.keys.forEach { add(JsonPrimitive(it)) } } }
            putJsonObject("currentDocument") { put("exists", true) }
        })
    }

    suspend fun deleteCard(uid: String, idToken: String, cardId: String) {
        commit(idToken, buildJsonObject { put("delete", cardPath(uid, cardId)) })
    }

    /**
     * Cancella documenti di users/{uid}/{collection} per id, a blocchi: un
     * commit ne regge fino a 500, qui 400 per stare larghi.
     */
    suspend fun deleteDocuments(uid: String, idToken: String, collection: String, ids: List<String>) {
        ids.chunked(400).forEach { chunk ->
            commitAll(idToken, chunk.map { id ->
                buildJsonObject { put("delete", "$database/documents/users/$uid/$collection/$id") }
            })
        }
    }

    /** Il profilo users/{uid}: l'ultimo documento, dopo le sottocartelle. */
    suspend fun deleteUserDocument(uid: String, idToken: String) {
        commit(idToken, buildJsonObject { put("delete", userPath(uid)) })
    }

    /** Un documento nuovo in users/{uid}/{collection} (collection.add() su Android). Restituisce l'id. */
    suspend fun createDocument(uid: String, idToken: String, collection: String, fields: Map<String, Any?>): String {
        val id = newDocumentId()
        commit(idToken, buildJsonObject {
            putJsonObject("update") {
                put("name", "$database/documents/users/$uid/$collection/$id")
                put("fields", toFields(fields))
            }
            putJsonObject("currentDocument") { put("exists", false) }
        })
        return id
    }

    /**
     * Un documento con l'id scelto da chi scrive, creato o rimpiazzato per
     * intero: docRef.set(map) su Android (followed_illustrators usa la chiave
     * dell'artista come id).
     */
    suspend fun setDocument(uid: String, idToken: String, collection: String, id: String, fields: Map<String, Any?>) {
        commit(idToken, buildJsonObject {
            putJsonObject("update") {
                put("name", "$database/documents/users/$uid/$collection/$id")
                put("fields", toFields(fields))
            }
        })
    }

    /** Aggiorna solo i campi dati di un documento esistente (docRef.update(map)). */
    suspend fun updateDocument(uid: String, idToken: String, collection: String, id: String, fields: Map<String, Any?>) {
        commit(idToken, buildJsonObject {
            putJsonObject("update") {
                put("name", "$database/documents/users/$uid/$collection/$id")
                put("fields", toFields(fields))
            }
            putJsonObject("updateMask") { putJsonArray("fieldPaths") { fields.keys.forEach { add(JsonPrimitive(it)) } } }
            putJsonObject("currentDocument") { put("exists", true) }
        })
    }

    /**
     * FieldValue.arrayUnion (add = true) o arrayRemove su un campo lista: come su
     * Android, senza leggere e riscrivere tutta la lista.
     */
    suspend fun changeArray(uid: String, idToken: String, collection: String, id: String, field: String, values: List<String>, add: Boolean) {
        if (values.isEmpty()) return
        commit(idToken, buildJsonObject {
            putJsonObject("transform") {
                put("document", "$database/documents/users/$uid/$collection/$id")
                putJsonArray("fieldTransforms") {
                    add(buildJsonObject {
                        put("fieldPath", field)
                        putJsonObject(if (add) "appendMissingElements" else "removeAllFromArray") {
                            put("values", JsonArray(values.map { toValue(it) }))
                        }
                    })
                }
            }
            putJsonObject("currentDocument") { put("exists", true) }
        })
    }

    /** FieldValue.increment su totalCards e totalValue di users/{uid}. */
    suspend fun incrementTotals(uid: String, idToken: String, cards: Long, value: Double) {
        if (cards == 0L && value == 0.0) return
        commit(idToken, buildJsonObject {
            putJsonObject("transform") {
                put("document", userPath(uid))
                putJsonArray("fieldTransforms") {
                    if (cards != 0L) add(buildJsonObject {
                        put("fieldPath", "totalCards")
                        putJsonObject("increment") { put("integerValue", cards.toString()) }
                    })
                    if (value != 0.0) add(buildJsonObject {
                        put("fieldPath", "totalValue")
                        putJsonObject("increment") { put("doubleValue", value) }
                    })
                }
            }
            // Come userDoc.update() su Android: se il profilo non c'e' non si crea.
            putJsonObject("currentDocument") { put("exists", true) }
        })
    }

    private suspend fun commit(idToken: String, write: JsonObject) = commitAll(idToken, listOf(write))

    private suspend fun commitAll(idToken: String, writes: List<JsonObject>) {
        val body = buildJsonObject { put("writes", JsonArray(writes)) }
        val response = client.post("$api:commit") {
            expectSuccess = false
            bearerAuth(idToken)
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
        check(response.status.isSuccess()) { "Firestore ${response.status.value}: ${response.bodyAsText()}" }
    }

    private fun equalFilter(field: String, value: String) = buildJsonObject {
        putJsonObject("fieldFilter") {
            putJsonObject("field") { put("fieldPath", field) }
            put("op", "EQUAL")
            putJsonObject("value") { put("stringValue", value) }
        }
    }

    companion object {
        private const val ID_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

        /** 20 caratteri alfanumerici, la forma degli id che crea l'SDK Firestore. */
        @OptIn(ExperimentalUuidApi::class)
        fun newDocumentId(): String {
            val bytes = Uuid.random().toByteArray() + Uuid.random().toByteArray()
            return (0 until 20).map { ID_ALPHABET[(bytes[it].toInt() and 0xff) % ID_ALPHABET.length] }.joinToString("")
        }

        /** Da valori Kotlin ai valori tipizzati di Firestore. */
        @OptIn(ExperimentalTime::class)
        fun toValue(value: Any?): JsonElement = when (value) {
            null -> buildJsonObject { put("nullValue", JsonNull) }
            is String -> buildJsonObject { put("stringValue", value) }
            is Boolean -> buildJsonObject { put("booleanValue", value) }
            is Int -> buildJsonObject { put("integerValue", value.toString()) }
            is Long -> buildJsonObject { put("integerValue", value.toString()) }
            is Double -> buildJsonObject { put("doubleValue", value) }
            is Float -> buildJsonObject { put("doubleValue", value.toDouble()) }
            is ServerNow -> buildJsonObject { put("timestampValue", Clock.System.now().toString()) }
            // Una data scelta dall'utente (il giorno di un torneo).
            is Timestamp -> buildJsonObject {
                put("timestampValue", Instant.fromEpochSeconds(value.seconds, value.nanoseconds).toString())
            }
            is List<*> -> buildJsonObject {
                putJsonObject("arrayValue") { put("values", JsonArray(value.map { toValue(it) })) }
            }
            else -> error("Tipo non previsto per Firestore: ${value::class.simpleName}")
        }

        fun toFields(fields: Map<String, Any?>): JsonObject = JsonObject(fields.mapValues { (_, v) -> toValue(v) })
    }
}
