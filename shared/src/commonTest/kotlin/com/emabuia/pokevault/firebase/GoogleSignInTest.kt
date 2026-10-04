package com.emabuia.pokevault.firebase

import com.emabuia.pokevault.data.AuthRepository
import com.emabuia.pokevault.data.FileCache
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.decodeURLQueryComponent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GoogleSignInTest {
    private val dir = Path(SystemTemporaryDirectory, "pokevault-google-${Random.nextLong().toULong()}").toString()
    private val bodies = mutableMapOf<String, String>()
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    @AfterTest
    fun cleanUp() {
        if (!SystemFileSystem.exists(Path(dir))) return
        SystemFileSystem.list(Path(dir)).forEach { SystemFileSystem.delete(it) }
        SystemFileSystem.delete(Path(dir))
    }

    private fun bodyText(request: HttpRequestData): String = when (val body = request.body) {
        is TextContent -> body.text
        is OutgoingContent.ByteArrayContent -> body.bytes().decodeToString()
        else -> ""
    }

    private val client = HttpClient(MockEngine { request ->
        val path = request.url.encodedPath
        bodies[path.substringAfterLast('/')] = bodyText(request)
        when {
            path == "/token" -> respond("""{"id_token":"google-id-token","access_token":"x"}""", HttpStatusCode.OK, json)
            path.endsWith("accounts:signInWithIdp") -> respond(
                """{"localId":"uid-g","email":"ash@gmail.com","idToken":"fb-id","refreshToken":"fb-r","expiresIn":"3600","displayName":"Ash Ketchum"}""",
                HttpStatusCode.OK, json,
            )
            path.endsWith("/users/uid-g") && request.method == HttpMethod.Get ->
                respond("""{"error":{"code":404}}""", HttpStatusCode.NotFound, json)
            path.endsWith("/users/uid-g") -> respond("{}", HttpStatusCode.OK, json)
            else -> respond("", HttpStatusCode.NotFound)
        }
    }) { expectSuccess = true }

    /** Fa le veci della pagina di Google: risponde col codice e lo state ricevuti. */
    private class FakeLauncher(val answer: (url: String) -> String) : GoogleAuthLauncher {
        var openedUrl = ""
        var scheme = ""
        override suspend fun authorize(url: String, callbackScheme: String): String {
            openedUrl = url; scheme = callbackScheme
            return answer(url)
        }
    }

    private fun param(url: String, name: String) =
        url.substringAfter("$name=").substringBefore('&').decodeURLQueryComponent()

    private val reversed = "com.googleusercontent.apps.123-abc"

    @Test
    fun googleAccountBecomesTheSameFirebaseAccountAsOnAndroid() = runTest {
        val launcher = FakeLauncher { url -> "$reversed:/oauth2redirect?state=${param(url, "state")}&code=4%2Fcodice&scope=email" }
        val google = GoogleSignIn(client, launcher, clientId = "123-abc.apps.googleusercontent.com", reversedClientId = reversed)
        val auth = AuthRepository(FirebaseAuthApi(client, "k"), FirestoreApi(client, "p"), FileCache(dir), now = { 0L })

        val session = auth.loginWithGoogle(google.idToken())

        // La pagina giusta, col client iOS, il ritorno nell'app e la challenge PKCE.
        assertEquals(reversed, launcher.scheme)
        assertEquals("123-abc.apps.googleusercontent.com", param(launcher.openedUrl, "client_id"))
        assertEquals("$reversed:/oauth2redirect", param(launcher.openedUrl, "redirect_uri"))
        assertEquals("S256", param(launcher.openedUrl, "code_challenge_method"))

        // Il codice scambiato col verifier che corrisponde alla challenge.
        val tokenRequest = bodies.getValue("token")
        val verifier = tokenRequest.substringAfter("code_verifier=").substringBefore('&')
        assertEquals(param(launcher.openedUrl, "code_challenge"), GoogleSignIn.challengeFor(verifier))
        assertTrue("code=4%2Fcodice" in tokenRequest)

        // L'id token di Google a Firebase, e il profilo col nome di Google.
        assertTrue("id_token=google-id-token&providerId=google.com" in bodies.getValue("accounts:signInWithIdp"))
        assertEquals("Ash Ketchum", session.name)
        assertTrue(""""name":{"stringValue":"Ash Ketchum"}""" in bodies.getValue("uid-g"))
    }

    @Test
    fun aDifferentStateIsRejected() = runTest {
        val launcher = FakeLauncher { "$reversed:/oauth2redirect?state=un-altro&code=abc" }
        val google = GoogleSignIn(client, launcher, clientId = "c", reversedClientId = reversed)
        assertFailsWith<GoogleSignInFailed> { google.idToken() }
        assertTrue("token" !in bodies) // nessuno scambio del codice
    }

    @Test
    fun saying_no_is_a_cancellation_not_an_error() = runTest {
        val launcher = FakeLauncher { url -> "$reversed:/oauth2redirect?state=${param(url, "state")}&error=access_denied" }
        val google = GoogleSignIn(client, launcher, clientId = "c", reversedClientId = reversed)
        assertFailsWith<GoogleSignInCancelled> { google.idToken() }
    }
}
