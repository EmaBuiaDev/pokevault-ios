package com.emabuia.pokevault.data.remote

import com.emabuia.pokevault.data.FileCache
import com.emabuia.pokevault.data.model.TournamentKind
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
import kotlinx.serialization.json.Json
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Meta Deck e Win Tournament contro un Limitless finto. */
class LimitlessTest {
    private val dir = Path(SystemTemporaryDirectory, "pokevault-limitless-${Random.nextLong().toULong()}").toString()
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val requested = mutableListOf<String>()

    @AfterTest
    fun cleanUp() {
        if (!SystemFileSystem.exists(Path(dir))) return
        SystemFileSystem.list(Path(dir)).forEach { SystemFileSystem.delete(it) }
        SystemFileSystem.delete(Path(dir))
    }

    // ── Le decklist nelle tre forme dell'API ────────────────────────────────

    @Test
    fun decklistsAreReadInAllThreeShapes() {
        val byCategory = LimitlessDecklists.parse(Json.parseToJsonElement(
            """{"pokemon":[{"count":4,"name":"Dreepy","set":"TWM","number":"128"}],
                "trainer":[{"count":"3","name":"Iono","set":"PAL","number":"185"}],
                "energy":[{"count":5,"name":"Basic Psychic Energy","set":"SVE","number":"5"}]}"""
        ))
        assertEquals(listOf("pokemon", "trainer", "energy"), byCategory.map { it.type })
        assertEquals(listOf(4, 3, 5), byCategory.map { it.qty })
        // La sigla passa da SetCodeMapper, come su Android: TWM diventa sv6.
        assertEquals(SetCodeMapper.normalizeDecklistSetCode("TWM"), byCategory.first().set)

        val flat = LimitlessDecklists.parse(Json.parseToJsonElement(
            """[{"count":2,"name":"Ultra Ball","set":"SVI","number":"196"},{"amount":1,"name":"Charizard ex"}]"""
        ))
        assertEquals(listOf("trainer", "pokemon"), flat.map { it.type })
        assertEquals(listOf(2, 1), flat.map { it.qty })

        val byId = LimitlessDecklists.parse(Json.parseToJsonElement("""{"deck":[{"id":"OBF_125","count":4}]}"""))
        assertEquals(SetCodeMapper.normalizeDecklistSetCode("OBF"), byId.single().set)
        assertEquals("125", byId.single().number)

        assertTrue(LimitlessDecklists.parse(null).isEmpty())
    }

    // ── Il limitatore ───────────────────────────────────────────────────────

    @Test
    fun theLimiterStopsBeforeTheWindowRunsOutAndAfterA429() {
        var clock = 0L
        val limiter = LimitlessRateLimiter(now = { clock })
        // 50 per finestra, con 6 di riserva: si passa 44 volte.
        assertEquals(44, (1..60).count { limiter.tryAcquire() })
        assertTrue(limiter.retryAfterSeconds() > 0)

        clock += 5 * 60 * 1000L
        assertTrue(limiter.tryAcquire())

        limiter.onTooManyRequests(retryAfterSeconds = 120)
        assertFalse(limiter.tryAcquire())
        assertEquals(121, limiter.retryAfterSeconds())
    }

    @Test
    fun theServerHeaderCountsToo() {
        val limiter = LimitlessRateLimiter(now = { 0L })
        limiter.onHeaders("limit=50, r=6, t=200")
        assertFalse(limiter.tryAcquire())
    }

    // ── Il repository ───────────────────────────────────────────────────────

    private fun standing(player: String, placing: Int?, deck: String, pokemon: String) =
        """{"player":"$player","name":"$player","placing":${placing ?: "null"},"record":{"wins":5,"losses":1,"ties":0},
            "deck":{"name":"$deck"},"decklist":{"pokemon":[{"count":4,"name":"$pokemon","set":"TWM","number":"128"}]}}"""

    private var tooManyRequests = false

    private val client = HttpClient(MockEngine { request ->
        val path = request.url.encodedPath
        requested += path
        when {
            tooManyRequests -> respond("", HttpStatusCode.TooManyRequests, headersOf("Retry-After", "90"))
            path.endsWith("/api/tournaments") -> respond(
                """[{"id":"t1","game":"PTCG","format":"standard","name":"Regional Milano","date":"2026-09-20T09:00:00Z","players":120},
                    {"id":"t2","game":"PTCG","format":"standard","name":"Serata al negozio","date":"2026-09-21T19:00:00Z","players":4},
                    {"id":"t3","game":"PTCG","format":"standard","name":"Online Cup","date":"2026-09-19T19:00:00Z","players":64}]""",
                HttpStatusCode.OK, json,
            )
            path.endsWith("/t1/details") -> respond("""{"id":"t1","isOnline":false,"organizer":{"id":1,"name":"Play!"}}""", HttpStatusCode.OK, json)
            path.endsWith("/t3/details") -> respond("""{"id":"t3","isOnline":true}""", HttpStatusCode.OK, json)
            path.endsWith("/t1/standings") -> respond(
                "[" + listOf(
                    standing("ash", 1, "Dragapult ex", "Dreepy"),
                    standing("misty", 2, "Gardevoir ex", "Ralts"),
                    standing("brock", null, "Dragapult ex", "Dreepy"),
                    standing("gary", 3, "Dragapult ex", "Dreepy"),
                ).joinToString(",") + "]",
                HttpStatusCode.OK, json,
            )
            path.endsWith("/t3/standings") -> respond("[" + standing("red", 1, "Gardevoir ex", "Ralts") + "]", HttpStatusCode.OK, json)
            path.endsWith("/t2/standings") -> respond("[]", HttpStatusCode.OK, json)
            else -> respond("", HttpStatusCode.NotFound)
        }
    })

    private fun repository() = LimitlessTcgRepository(client, FileCache(dir))

    @Test
    fun archetypesAreAggregatedWithWithdrawnPlayersLast() = runTest {
        val archetypes = repository().getMetaArchetypes().getOrThrow()
        val dragapult = archetypes.first()
        assertEquals("Dragapult ex", dragapult.name)
        assertEquals(3, dragapult.count)
        // Il ritirato (piazzamento mancante) non diventa "Top 0".
        assertEquals(1, dragapult.topPlacement)
        assertEquals(listOf(1, 3), dragapult.recentResults)
        assertEquals("Dreepy", dragapult.sampleDeck!!.cards.single().name)
    }

    @Test
    fun liveTournamentsSkipTheSmallOnesAndTheOnlineOnes() = runTest {
        val results = repository().getTournamentResults(kind = TournamentKind.LIVE).getOrThrow()
        assertEquals(listOf("Regional Milano"), results.map { it.tournamentName })
        assertEquals(listOf(1, 2, 3), results.single().top3.map { it.placement })
        assertEquals("Play!", results.single().organizerName)
        // La serata da quattro giocatori non si chiede nemmeno.
        assertFalse(requested.any { it.contains("/t2/") })
    }

    @Test
    fun aSecondLoadComesFromTheCacheEvenAfterARestart() = runTest {
        repository().getMetaArchetypes().getOrThrow()
        val before = requested.size
        repository().getMetaArchetypes().getOrThrow()
        assertEquals(before, requested.size)
    }

    @Test
    fun a429BecomesARateLimitWithTheServerWait() = runTest {
        tooManyRequests = true
        val error = repository().getMetaArchetypes().exceptionOrNull()
        assertTrue(error is LimitlessRateLimitException)
        assertTrue(error.retryAfterSeconds in 90..91)
    }
}
