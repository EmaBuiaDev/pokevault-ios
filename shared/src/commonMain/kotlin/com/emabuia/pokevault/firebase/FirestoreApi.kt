package com.emabuia.pokevault.firebase

import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Il profilo in users/{uid}, lo stesso documento che crea l'app Android. */
data class UserProfile(val name: String, val email: String)

/**
 * Firestore via REST, con l'id token dell'utente: valgono le stesse regole di
 * sicurezza dell'app Android, come se la richiesta venisse da li'.
 *
 * Per ora solo letture della collezione e il profilo: le scritture sulle
 * carte arrivano dopo, con i loro test.
 */
class FirestoreApi(
    private val client: HttpClient,
    private val projectId: String = FirebaseConfig.PROJECT_ID,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val documents get() = "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents"

    /** Il profilo, o null se il documento non c'e' ancora. */
    suspend fun getProfile(uid: String, idToken: String): UserProfile? {
        val response = client.get("$documents/users/$uid") {
            expectSuccess = false
            bearerAuth(idToken)
        }
        if (response.status == HttpStatusCode.NotFound) return null
        val text = response.bodyAsText()
        check(response.status.isSuccess()) { "Firestore ${response.status.value}" }
        val fields = json.parseToJsonElement(text).jsonObject["fields"]?.jsonObject ?: return UserProfile("", "")
        return UserProfile(name = fields.string("name"), email = fields.string("email"))
    }

    /**
     * Crea il profilo con gli stessi campi dell'app Android
     * (FirebaseAuthManager.register): name, email, createdAt, totalCards.
     */
    @OptIn(ExperimentalTime::class)
    suspend fun createProfile(uid: String, idToken: String, name: String, email: String) {
        val body = """
            {"fields":{
              "name":{"stringValue":${Json.encodeToString(name)}},
              "email":{"stringValue":${Json.encodeToString(email)}},
              "createdAt":{"timestampValue":"${Clock.System.now()}"},
              "totalCards":{"integerValue":"0"}
            }}
        """.trimIndent()
        val response = client.patch("$documents/users/$uid") {
            expectSuccess = false
            bearerAuth(idToken)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        check(response.status.isSuccess()) { "Firestore ${response.status.value}" }
    }

    /**
     * Tutti i documenti di users/{uid}/cards, pagina per pagina, come JSON
     * semplice (vedi [firestoreFieldsToJson]) con l'id del documento.
     */
    suspend fun listCards(uid: String, idToken: String): List<Pair<String, JsonObject>> {
        val result = mutableListOf<Pair<String, JsonObject>>()
        var pageToken: String? = null
        do {
            val response = client.get("$documents/users/$uid/cards") {
                expectSuccess = false
                bearerAuth(idToken)
                url.parameters.append("pageSize", "300")
                pageToken?.let { url.parameters.append("pageToken", it) }
            }
            val text = response.bodyAsText()
            check(response.status.isSuccess()) { "Firestore ${response.status.value}" }
            val page = json.parseToJsonElement(text).jsonObject
            page["documents"]?.jsonArray?.forEach { element ->
                val document = element.jsonObject
                val id = document["name"]?.jsonPrimitive?.content?.substringAfterLast('/').orEmpty()
                val fields = document["fields"]?.jsonObject ?: JsonObject(emptyMap())
                result += id to firestoreFieldsToJson(fields)
            }
            pageToken = page["nextPageToken"]?.jsonPrimitive?.content
        } while (pageToken != null)
        return result
    }

    private fun JsonObject.string(name: String) =
        this[name]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content.orEmpty()
}
