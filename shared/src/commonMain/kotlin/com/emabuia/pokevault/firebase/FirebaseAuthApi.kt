package com.emabuia.pokevault.firebase

import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Quello che Firebase Auth restituisce a un accesso riuscito. */
@Serializable
data class AuthTokens(
    val localId: String,
    val email: String = "",
    val idToken: String,
    val refreshToken: String,
    val expiresIn: String = "3600",
    val displayName: String = "",
)

@Serializable
private data class RefreshResponse(
    @SerialName("user_id") val userId: String,
    @SerialName("id_token") val idToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: String = "3600",
)

/**
 * Un rifiuto di Firebase Auth: [code] e' il codice della REST API
 * ("EMAIL_EXISTS", "INVALID_LOGIN_CREDENTIALS"...), da tradurre per l'utente.
 */
class FirebaseAuthException(val code: String) : Exception(code)

/**
 * Firebase Auth via REST (Identity Toolkit), senza l'SDK nativo.
 *
 * L'SDK su iOS va aggiunto al progetto Xcode, che da Windows non si puo'
 * provare; le REST API sono le stesse che l'SDK usa sotto, girano uguali su
 * iOS, Android e desktop e si provano con un test JVM. Stessi account
 * dell'app Android: e' lo stesso progetto Firebase.
 */
class FirebaseAuthApi(
    private val client: HttpClient,
    private val apiKey: String = FirebaseConfig.API_KEY,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun signUp(email: String, password: String): AuthTokens =
        accounts("signUp", """{"email":${quote(email)},"password":${quote(password)},"returnSecureToken":true}""")

    suspend fun signIn(email: String, password: String): AuthTokens =
        accounts("signInWithPassword", """{"email":${quote(email)},"password":${quote(password)},"returnSecureToken":true}""")

    suspend fun sendPasswordReset(email: String) {
        accounts<Unit>("sendOobCode", """{"requestType":"PASSWORD_RESET","email":${quote(email)}}""", decode = false)
    }

    /** Un id token nuovo dal refresh token: quello vecchio scade dopo un'ora. */
    suspend fun refresh(refreshToken: String): AuthTokens {
        val response = client.submitForm(
            url = "https://securetoken.googleapis.com/v1/token?key=$apiKey",
            formParameters = parameters {
                append("grant_type", "refresh_token")
                append("refresh_token", refreshToken)
            },
        ) { expectSuccess = false }
        val body = checked(response)
        val refreshed = json.decodeFromString<RefreshResponse>(body)
        return AuthTokens(
            localId = refreshed.userId,
            idToken = refreshed.idToken,
            refreshToken = refreshed.refreshToken,
            expiresIn = refreshed.expiresIn,
        )
    }

    private suspend inline fun <reified T> accounts(method: String, body: String, decode: Boolean = true): T {
        val response = client.post("https://identitytoolkit.googleapis.com/v1/accounts:$method?key=$apiKey") {
            expectSuccess = false
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val text = checked(response)
        @Suppress("UNCHECKED_CAST")
        return if (decode) json.decodeFromString<T>(text) else Unit as T
    }

    private suspend fun checked(response: HttpResponse): String {
        val text = response.bodyAsText()
        if (response.status.isSuccess()) return text
        // {"error":{"code":400,"message":"WEAK_PASSWORD : Password should be at least 6 characters"}}
        val message = runCatching {
            json.parseToJsonElement(text).jsonObject["error"]!!.jsonObject["message"]!!.jsonPrimitive.content
        }.getOrDefault("HTTP_${response.status.value}")
        throw FirebaseAuthException(message.substringBefore(" ").trim())
    }

    private fun quote(value: String) = Json.encodeToString(value)
}
