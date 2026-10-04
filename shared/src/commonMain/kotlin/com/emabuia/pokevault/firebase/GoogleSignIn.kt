package com.emabuia.pokevault.firebase

import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.decodeURLQueryComponent
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Apre la pagina di accesso di Google e restituisce l'URL con cui Google
 * richiama l'app. E' l'unico pezzo che cambia da piattaforma a piattaforma:
 * su iOS e' ASWebAuthenticationSession.
 */
interface GoogleAuthLauncher {
    /** @throws GoogleSignInCancelled se l'utente chiude la pagina, [GoogleSignInFailed] negli altri casi. */
    suspend fun authorize(url: String, callbackScheme: String): String
}

/** L'utente ha chiuso la pagina di Google o ha detto di no: non e' un errore da mostrare. */
class GoogleSignInCancelled : Exception("Accesso con Google annullato")

/** L'accesso con Google non e' riuscito per un motivo da dire all'utente. */
class GoogleSignInFailed(message: String) : Exception(message)

/** Dove l'accesso con Google non c'e' (Android di prova, desktop). */
class UnsupportedGoogleAuthLauncher : GoogleAuthLauncher {
    override suspend fun authorize(url: String, callbackScheme: String): String =
        throw UnsupportedOperationException("Accesso con Google disponibile solo su iPhone")
}

/**
 * Accesso con Google senza l'SDK: OAuth 2.0 con PKCE, il flusso che Google
 * prevede per le app installate, verso il client iOS che Firebase ha creato
 * registrando l'app (CLIENT_ID nel GoogleService-Info.plist).
 *
 * Ne esce l'id token di Google, che FirebaseAuthApi.signInWithGoogle scambia
 * per una sessione Firebase: lo stesso account che si ottiene con Google
 * sull'app Android.
 */
class GoogleSignIn(
    private val client: HttpClient,
    private val launcher: GoogleAuthLauncher,
    private val clientId: String = FirebaseConfig.IOS_CLIENT_ID,
    private val reversedClientId: String = FirebaseConfig.REVERSED_CLIENT_ID,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val redirectUri get() = "$reversedClientId:/oauth2redirect"

    /** L'id token di Google dell'utente che ha fatto l'accesso. */
    suspend fun idToken(): String {
        val verifier = randomToken(64)
        val state = randomToken(24)
        val url = "https://accounts.google.com/o/oauth2/v2/auth" +
            "?client_id=${clientId.encodeURLParameter()}" +
            "&redirect_uri=${redirectUri.encodeURLParameter()}" +
            "&response_type=code" +
            "&scope=${"openid email profile".encodeURLParameter()}" +
            "&code_challenge=${challengeFor(verifier)}" +
            "&code_challenge_method=S256" +
            "&state=$state" +
            "&prompt=select_account"

        val callback = launcher.authorize(url, reversedClientId)
        val query = parseQuery(callback)
        when (val error = query["error"]) {
            null -> Unit
            "access_denied" -> throw GoogleSignInCancelled()
            else -> throw GoogleSignInFailed("Google ha rifiutato l'accesso: $error")
        }
        // Lo state deve tornare uguale: altrimenti la risposta non e' la nostra.
        if (query["state"] != state) throw GoogleSignInFailed("Risposta di Google non valida (state diverso)")
        val code = query["code"] ?: throw GoogleSignInFailed("Google non ha restituito il codice: $callback")

        val response = client.submitForm(
            url = "https://oauth2.googleapis.com/token",
            formParameters = parameters {
                append("code", code)
                append("client_id", clientId)
                append("redirect_uri", redirectUri)
                append("grant_type", "authorization_code")
                append("code_verifier", verifier)
            },
        ) { expectSuccess = false }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            // La risposta di Google dice il perche' (redirect_uri_mismatch, invalid_grant...).
            val reason = runCatching {
                val body = json.parseToJsonElement(text).jsonObject
                listOfNotNull(body["error"]?.jsonPrimitive?.content, body["error_description"]?.jsonPrimitive?.content).joinToString(": ")
            }.getOrNull().orEmpty()
            throw GoogleSignInFailed("Google ha rifiutato il codice (${response.status.value}) $reason".trim())
        }
        return json.parseToJsonElement(text).jsonObject["id_token"]?.jsonPrimitive?.content
            ?: throw GoogleSignInFailed("Google non ha restituito l'id token")
    }

    companion object {
        /** code_challenge = BASE64URL(SHA256(verifier)) senza padding (RFC 7636). */
        @OptIn(ExperimentalEncodingApi::class)
        fun challengeFor(verifier: String): String =
            Base64.UrlSafe.encode(Sha256.digest(verifier.encodeToByteArray())).trimEnd('=')

        /**
         * Verifier e state devono essere imprevedibili. Uuid.random() usa per
         * contratto un generatore crittografico su ogni piattaforma, cosa che
         * kotlin.random.Random non promette. 32 cifre esadecimali per Uuid.
         */
        @OptIn(ExperimentalUuidApi::class)
        private fun randomToken(length: Int) = buildString {
            while (this.length < length) append(Uuid.random().toHexString())
        }.take(length)

        /** I parametri di "schema:/oauth2redirect?code=..&state=..". */
        fun parseQuery(url: String): Map<String, String> =
            url.substringAfter('?', "").substringBefore('#').split('&')
                .filter { '=' in it }
                .associate { pair ->
                    val (key, value) = pair.split('=', limit = 2)
                    key to value.decodePercent()
                }

        private fun String.decodePercent(): String = decodeURLQueryComponent()
    }
}
