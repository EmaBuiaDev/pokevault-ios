package com.emabuia.pokevault.data

import com.emabuia.pokevault.firebase.FirebaseAuthApi
import com.emabuia.pokevault.firebase.FirestoreApi
import com.emabuia.pokevault.firebase.FirestoreWrites
import com.emabuia.pokevault.firebase.GoogleSignIn
import com.emabuia.pokevault.firebase.UnsupportedGoogleAuthLauncher
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
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** La cancellazione dell'account contro un Firebase finto: niente account veri toccati. */
class AccountDeleterTest {
    private val dir = Path(SystemTemporaryDirectory, "pokevault-delete-${Random.nextLong().toULong()}").toString()
    private val STRINGS = ListSerializer(String.serializer())
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    /** Ogni chiamata, nell'ordine: "METODO percorso" (e per i commit cosa cancellano). */
    private val calls = mutableListOf<String>()
    private var tradeStatus = HttpStatusCode.NoContent
    private var tradeBody = ""
    private var reauthUid = "uid-1"

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
        val body = bodyText(request)
        calls += if (path.endsWith(":commit")) "COMMIT ${Regex("\"delete\":\"[^\"]*/(users/[^\"]+)\"").findAll(body).joinToString(",") { it.groupValues[1] }}"
        else "${request.method.value} ${path.substringAfterLast("/v1/")}"
        when {
            path.endsWith("accounts:signInWithPassword") -> respond(
                """{"localId":"$reauthUid","email":"ash@gmail.com","idToken":"fresh","refreshToken":"r","expiresIn":"3600"}""",
                HttpStatusCode.OK, json,
            )
            path.endsWith("/users/uid-1") && request.method == HttpMethod.Get ->
                respond("""{"fields":{"name":{"stringValue":"Ash"}}}""", HttpStatusCode.OK, json)
            path.endsWith("/v1/trade/profile") -> respond(tradeBody, tradeStatus, json)
            path.endsWith("/users/uid-1/cards") -> respond(
                """{"documents":[{"name":"x/cards/c1","fields":{}},{"name":"x/cards/c2","fields":{}}]}""", HttpStatusCode.OK, json,
            )
            path.contains("/users/uid-1/") -> respond("""{}""", HttpStatusCode.OK, json) // sottocartelle vuote
            path.endsWith(":commit") -> respond("{}", HttpStatusCode.OK, json)
            path.endsWith("accounts:delete") -> respond("{}", HttpStatusCode.OK, json)
            else -> respond("", HttpStatusCode.NotFound)
        }
    }) { expectSuccess = true }

    private suspend fun setUp(): Pair<AccountDeleter, AuthRepository> {
        val store = FileCache(dir)
        val authApi = FirebaseAuthApi(client, "k")
        val auth = AuthRepository(authApi, FirestoreApi(client, "p"), store, now = { 0L })
        auth.login("ash@gmail.com", "pikachu")
        store.write("collection_uid-1", STRINGS, listOf("x"))
        calls.clear()
        val deleter = AccountDeleter(
            auth, authApi, FirestoreApi(client, "p"), FirestoreWrites(client, "p"),
            GoogleSignIn(client, UnsupportedGoogleAuthLauncher(), "c", "r"), client, store, tradeBaseUrl = "https://w.dev",
        )
        return deleter to auth
    }

    @Test
    fun everythingGoesInTheSameOrderAsAndroid() = runTest {
        val (deleter, auth) = setUp()
        deleter.deleteAccount(Reauthentication.Password("pikachu"))

        assertEquals("POST accounts:signInWithPassword", calls[0].substringAfterLast("/"))
        assertEquals("DELETE trade/profile", calls[1])
        // Le carte, poi le altre sottocartelle (vuote: nessun commit), poi il profilo, poi l'account.
        assertTrue("COMMIT users/uid-1/cards/c1,users/uid-1/cards/c2" in calls)
        val profile = calls.indexOf("COMMIT users/uid-1")
        val account = calls.indexOfFirst { it.endsWith("accounts:delete") }
        assertTrue(profile in 0 until account, "il profilo prima dell'account: $calls")
        AccountDeleter.SUBCOLLECTIONS.forEach { name -> assertTrue(calls.any { it.endsWith("/$name") }, "manca $name") }

        assertNull(auth.session.value)
        assertNull(FileCache(dir).read("collection_uid-1", STRINGS))
    }

    @Test
    fun noProfileOnTradeRadarIsFine() = runTest {
        tradeStatus = HttpStatusCode.NotFound
        tradeBody = """{"error":"no_profile"}"""
        val (deleter, auth) = setUp()
        deleter.deleteAccount(Reauthentication.Password("pikachu"))
        assertNull(auth.session.value)
    }

    @Test
    fun ifTradeRadarDoesNotAnswerNothingIsDeleted() = runTest {
        tradeStatus = HttpStatusCode.InternalServerError
        val (deleter, auth) = setUp()
        assertFailsWith<IllegalStateException> { deleter.deleteAccount(Reauthentication.Password("pikachu")) }
        assertTrue(calls.none { it.startsWith("COMMIT") || it.endsWith("accounts:delete") }, "$calls")
        assertNotNull(auth.session.value)
    }

    @Test
    fun confirmingWithAnotherAccountIsRefused() = runTest {
        val (deleter, auth) = setUp()
        // Collegato come uid-1, ma la password digitata e' di un altro account.
        reauthUid = "uid-altro"
        assertFailsWith<IllegalStateException> { deleter.deleteAccount(Reauthentication.Password("pikachu")) }
        assertTrue(calls.none { it.startsWith("COMMIT") || it.contains("trade") }, "$calls")
        assertNotNull(auth.session.value)
    }
}
