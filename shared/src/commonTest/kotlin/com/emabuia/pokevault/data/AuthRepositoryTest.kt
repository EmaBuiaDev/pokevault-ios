package com.emabuia.pokevault.data

import com.emabuia.pokevault.firebase.FirebaseAuthApi
import com.emabuia.pokevault.firebase.FirebaseAuthException
import com.emabuia.pokevault.firebase.FirestoreApi
import com.emabuia.pokevault.screens.auth.AuthViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Il login contro un Firebase finto (MockEngine): niente account veri creati
 * nel progetto di produzione per far girare i test.
 */
class AuthRepositoryTest {
    private val dir = Path(SystemTemporaryDirectory, "pokevault-auth-${Random.nextLong().toULong()}").toString()
    private var clock = 1_000_000L
    private val calls = mutableListOf<String>()
    private val bodies = mutableListOf<String>()

    /** Cosa risponde il Firebase finto, per chiamata. */
    private var profileExists = false
    private var signInError: String? = null
    private var refreshError: String? = null

    @AfterTest
    fun cleanUp() {
        if (!SystemFileSystem.exists(Path(dir))) return
        SystemFileSystem.list(Path(dir)).forEach { SystemFileSystem.delete(it) }
        SystemFileSystem.delete(Path(dir))
    }

    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private val client = HttpClient(MockEngine { request ->
        val path = request.url.encodedPath
        calls += "${request.method.value} ${request.url.host}$path"
        bodyText(request)?.let { bodies += it }
        when {
            path.endsWith("accounts:signUp") ->
                respond(TOKENS, HttpStatusCode.OK, json)
            path.endsWith("accounts:signInWithPassword") -> signInError
                ?.let { respond("""{"error":{"code":400,"message":"$it"}}""", HttpStatusCode.BadRequest, json) }
                ?: respond(TOKENS, HttpStatusCode.OK, json)
            path.endsWith("/v1/token") -> refreshError
                ?.let { respond("""{"error":{"code":400,"message":"$it"}}""", HttpStatusCode.BadRequest, json) }
                ?: respond(
                    """{"user_id":"uid-1","id_token":"id-2","refresh_token":"refresh-2","expires_in":"3600"}""",
                    HttpStatusCode.OK, json,
                )
            path.endsWith("/users/uid-1") && request.method == HttpMethod.Get ->
                if (profileExists) {
                    respond("""{"fields":{"name":{"stringValue":"Ash"},"email":{"stringValue":"ash@gmail.com"}}}""", HttpStatusCode.OK, json)
                } else {
                    respond("""{"error":{"code":404}}""", HttpStatusCode.NotFound, json)
                }
            path.endsWith("/users/uid-1") && request.method == HttpMethod.Patch ->
                respond("{}", HttpStatusCode.OK, json)
            else -> respond("", HttpStatusCode.NotFound)
        }
    }) { expectSuccess = true }

    private fun bodyText(request: HttpRequestData): String? = when (val body = request.body) {
        is TextContent -> body.text
        is OutgoingContent.ByteArrayContent -> body.bytes().decodeToString()
        else -> null
    }

    private fun repository() = AuthRepository(
        auth = FirebaseAuthApi(client, apiKey = "test-key"),
        firestore = FirestoreApi(client, projectId = "test-project"),
        store = FileCache(dir, now = { clock }),
        now = { clock },
    )

    @Test
    fun registerCreatesTheSameProfileAsAndroid() = runTest {
        val session = repository().register("misty@gmail.com", "segreta", "Misty")
        assertEquals("Misty", session.name)
        assertEquals("uid-1", session.uid)

        assertTrue("PATCH firestore.googleapis.com/v1/projects/test-project/databases/(default)/documents/users/uid-1" in calls)
        val profile = bodies.single { "totalCards" in it }
        assertTrue(""""name":{"stringValue":"Misty"}""" in profile)
        assertTrue(""""totalCards":{"integerValue":"0"}""" in profile)
        assertTrue(""""createdAt":{"timestampValue":""" in profile)
    }

    @Test
    fun loginTakesTheNameFromTheProfileAndSurvivesARestart() = runTest {
        profileExists = true
        repository().login("ash@gmail.com", "pikachu")
        // Nessun profilo riscritto se c'e' gia'.
        assertTrue(calls.none { it.startsWith("PATCH") })

        val restarted = repository()
        assertEquals("Ash", restarted.session.value?.name)
    }

    @Test
    fun wrongPasswordBecomesTheItalianMessage() = runTest {
        signInError = "INVALID_LOGIN_CREDENTIALS"
        val error = assertFailsWith<FirebaseAuthException> { repository().login("ash@gmail.com", "sbagliata") }
        assertEquals("Email o password non corretta", AuthViewModel.messageFor(error))

        signInError = "WEAK_PASSWORD : Password should be at least 6 characters"
        val weak = assertFailsWith<FirebaseAuthException> { repository().login("ash@gmail.com", "x") }
        assertEquals("WEAK_PASSWORD", weak.code)
    }

    @Test
    fun anExpiredTokenIsRefreshedAndARevokedOneLogsOut() = runTest {
        profileExists = true
        val repository = repository()
        repository.login("ash@gmail.com", "pikachu")
        assertEquals("id-1", repository.validIdToken())

        // Dopo un'ora il token e' scaduto: se ne chiede uno nuovo.
        clock += 60L * 60 * 1000
        assertEquals("id-2", repository.validIdToken())
        assertEquals("refresh-2", repository().session.value?.refreshToken)

        // Sessione revocata (password cambiata altrove): si esce.
        clock += 60L * 60 * 1000
        refreshError = "TOKEN_EXPIRED"
        assertNull(repository.validIdToken())
        assertNull(repository.session.value)
        assertNull(repository().session.value)
    }

    private companion object {
        const val TOKENS =
            """{"localId":"uid-1","email":"ash@gmail.com","idToken":"id-1","refreshToken":"refresh-1","expiresIn":"3600"}"""
    }
}
