package com.emabuia.pokevault.data

import com.emabuia.pokevault.firebase.FirebaseAuthApi
import com.emabuia.pokevault.firebase.FirestoreApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Senza rete vale l'ultimo esito certo dello stesso account, per 7 giorni
 * (SubscriptionMemory, come su Android).
 */
class PremiumMemoryTest {
    private val dir = Path(SystemTemporaryDirectory, "pokevault-premium-${Random.nextLong().toULong()}").toString()
    private var clock = 1_000_000L
    private var online = true
    private var entitled = true
    private var giftUntil = 0L
    private var user = "uid-1"

    @AfterTest
    fun cleanUp() {
        if (!SystemFileSystem.exists(Path(dir))) return
        SystemFileSystem.list(Path(dir)).forEach { SystemFileSystem.delete(it) }
        SystemFileSystem.delete(Path(dir))
    }

    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private val client = HttpClient(MockEngine { request ->
        val path = request.url.encodedPath
        when {
            path.endsWith("accounts:signInWithPassword") -> respond(
                """{"localId":"$user","email":"ash@gmail.com","idToken":"id-1","refreshToken":"r-1","expiresIn":"999999"}""",
                HttpStatusCode.OK, json,
            )
            path.endsWith("/users/$user") -> respond("""{"fields":{"name":{"stringValue":"Ash"}}}""", HttpStatusCode.OK, json)
            !online -> error("offline")
            path.endsWith("/v1/billing/entitlement") -> respond("""{"entitled":$entitled}""", HttpStatusCode.OK, json)
            path.endsWith("/v1/gift/me") -> respond("""{"giftUntilMs":$giftUntil}""", HttpStatusCode.OK, json)
            else -> respond("", HttpStatusCode.NotFound)
        }
    }) { expectSuccess = true }

    private val store = FileCache(dir, now = { clock })
    private val auth = AuthRepository(
        auth = FirebaseAuthApi(client, apiKey = "k"),
        firestore = FirestoreApi(client, projectId = "p"),
        store = store,
        now = { clock },
    )
    private val premium = PremiumRepository(auth, client, now = { clock }, baseUrl = "https://w", store = store)

    @Test
    fun offlineTheLastAnswerHoldsForSevenDays() = runTest {
        auth.login("ash@gmail.com", "pikachu")
        assertEquals(true, premium.isPremium())

        online = false
        clock += 6L * 24 * 60 * 60 * 1000
        assertEquals(true, premium.isPremium())

        clock += 2L * 24 * 60 * 60 * 1000
        assertNull(premium.isPremium())
    }

    @Test
    fun aCancelledSubscriptionIsNotRememberedAsPremium() = runTest {
        auth.login("ash@gmail.com", "pikachu")
        entitled = false
        assertEquals(false, premium.isPremium())

        online = false
        assertNull(premium.isPremium())
    }

    @Test
    fun theGiftKeepsItsExpiryOffline() = runTest {
        auth.login("ash@gmail.com", "pikachu")
        entitled = false
        giftUntil = clock + 1_000
        assertEquals(true, premium.isPremium())

        online = false
        assertEquals(true, premium.isPremium())
        clock += 2_000
        assertNull(premium.isPremium())
    }

    @Test
    fun anotherAccountDoesNotInheritIt() = runTest {
        auth.login("ash@gmail.com", "pikachu")
        assertEquals(true, premium.isPremium())

        auth.logout()
        user = "uid-2"
        auth.login("misty@gmail.com", "starmie")
        online = false
        assertNull(premium.isPremium())
    }
}
