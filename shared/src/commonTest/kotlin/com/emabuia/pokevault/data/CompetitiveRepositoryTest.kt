package com.emabuia.pokevault.data

import com.emabuia.pokevault.data.model.Deck
import com.emabuia.pokevault.data.model.MatchLog
import com.emabuia.pokevault.data.model.Timestamp
import com.emabuia.pokevault.data.model.Tournament
import com.emabuia.pokevault.firebase.FirebaseAuthApi
import com.emabuia.pokevault.firebase.FirestoreApi
import com.emabuia.pokevault.firebase.FirestoreWrites
import com.emabuia.pokevault.util.formatDayMonthYear
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tornei, partite e mazzi contro un Firestore finto: gli stessi documenti di Android. */
class CompetitiveRepositoryTest {
    private val dir = Path(SystemTemporaryDirectory, "pokevault-comp-${Random.nextLong().toULong()}").toString()
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val writes = mutableListOf<JsonObject>()

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
        when {
            path.endsWith("accounts:signInWithPassword") -> respond(
                """{"localId":"uid-1","email":"ash@gmail.com","idToken":"id-1","refreshToken":"r-1","expiresIn":"3600"}""",
                HttpStatusCode.OK, json,
            )
            path.endsWith("/users/uid-1") -> respond("""{"fields":{"name":{"stringValue":"Ash"}}}""", HttpStatusCode.OK, json)
            path.endsWith("/users/uid-1/tournaments") -> respond(TOURNAMENTS, HttpStatusCode.OK, json)
            path.endsWith("/users/uid-1/match_logs") -> respond(MATCHES, HttpStatusCode.OK, json)
            path.endsWith("/users/uid-1/decks") -> respond(DECKS, HttpStatusCode.OK, json)
            path.endsWith(":commit") -> {
                writes += Json.parseToJsonElement(bodyText(request)).jsonObject["writes"]!!.jsonArray.map { it.jsonObject }
                respond("{}", HttpStatusCode.OK, json)
            }
            else -> respond("", HttpStatusCode.NotFound)
        }
    }) { expectSuccess = true }

    private suspend fun repository(): CompetitiveRepository {
        val store = FileCache(dir)
        val auth = AuthRepository(FirebaseAuthApi(client, "k"), FirestoreApi(client, "p"), store, now = { 0L })
        auth.login("ash@gmail.com", "pikachu")
        return CompetitiveRepository(FirestoreApi(client, "p"), auth, store, FirestoreWrites(client, "p"))
    }

    private fun JsonObject.fields() = this["update"]!!.jsonObject["fields"]!!.jsonObject
    private fun JsonObject.value(field: String, kind: String) =
        fields()[field]!!.jsonObject[kind]!!.jsonPrimitive.content

    @Test
    fun tournamentsMatchesAndDecksAreReadAsAndroidWritesThem() = runTest {
        val repo = repository()

        val tournaments = repo.tournaments()
        // Dal piu' recente; quello senza data non resta fuori (su Android si')
        assertEquals(listOf("Cup", "Challenge", "Local"), tournaments.map { it.type })
        val cup = tournaments.first()
        assertEquals(64, cup.participants)
        assertEquals(15.0, cup.registrationFee)
        assertEquals("Dragapult", cup.deckName)
        assertEquals("21/09/2026", formatDayMonthYear(cup.date!!, TimeZone.of("Europe/Rome")))

        val matches = repo.matchLogs()
        assertEquals(listOf("m2", "m1"), matches.map { it.id })
        assertEquals(2, matches.first().round)

        assertEquals(listOf("Dragapult"), repo.decks().map { it.name })
    }

    @Test
    fun aNewTournamentHasAndroidsFieldsAndTheChosenDate() = runTest {
        repository().saveTournament(
            Tournament(
                location = "Game Store Milano", date = Timestamp.fromEpochMillis(1_790_000_000_000),
                participants = 32, registrationFee = 10.0, type = "Local", format = "Standard",
            )
        )
        val write = writes.single()
        assertEquals(
            setOf("location", "date", "participants", "registrationFee", "type", "format", "deckName", "deckId", "createdAt"),
            write.fields().keys,
        )
        assertEquals("2026-09-21T14:13:20Z", write.value("date", "timestampValue"))
        assertEquals("32", write.value("participants", "integerValue"))
        assertEquals("10.0", write.value("registrationFee", "doubleValue"))
        // Documento nuovo: mai sovrascriverne uno per errore.
        assertTrue("currentDocument" in write)
    }

    @Test
    fun editingRewritesTheWholeDocumentButKeepsItsCreationDate() = runTest {
        val created = Timestamp(seconds = 1_780_000_000)
        repository().saveMatchLog(MatchLog(id = "m1", tournamentId = "t1", round = 3, result = "W", createdAt = created))
        val write = writes.single()
        // set() su Android: niente updateMask, il documento si riscrive intero.
        assertFalse("updateMask" in write)
        assertTrue(write["update"]!!.jsonObject["name"]!!.jsonPrimitive.content.endsWith("/match_logs/m1"))
        assertEquals("2026-05-28T20:26:40Z", write.value("createdAt", "timestampValue"))
    }

    @Test
    fun aDuplicatedDeckHasAndroidsFieldsAndStaysATestDeck() = runTest {
        repository().saveDeck(
            Deck(name = "Dragapult (Copia)", cards = listOf("c1", "c1"), totalCards = 2, deckOnly = true,
                coverImageUrls = listOf("https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/887.png"))
        )
        val write = writes.single()
        assertEquals(
            setOf("name", "cards", "mainTypes", "averageHp", "totalCards", "recommendedEnergy", "coverImageUrl", "coverImageUrls", "deckOnly", "createdAt"),
            write.fields().keys,
        )
        assertEquals("true", write.value("deckOnly", "booleanValue"))
        // Le copie sono voci ripetute dell'array, come su Android.
        assertEquals(2, write.fields()["cards"]!!.jsonObject["arrayValue"]!!.jsonObject["values"]!!.jsonArray.size)
    }

    @Test
    fun deckOnlyCardsAreDeletedWithoutTouchingTheProfileTotals() = runTest {
        repository().deleteDeckOnlyCards(listOf("c1", "c2"))
        val deleted = writes.map { it["delete"]!!.jsonPrimitive.content.substringAfter("/documents/users/uid-1/") }
        assertEquals(listOf("cards/c1", "cards/c2"), deleted)
        assertTrue(writes.none { "transform" in it })
    }

    @Test
    fun deletingATournamentDeletesItsMatchesFirst() = runTest {
        repository().deleteTournament("t1")
        val deleted = writes.map { it["delete"]!!.jsonPrimitive.content.substringAfter("/documents/users/uid-1/") }
        assertEquals(setOf("match_logs/m1", "match_logs/m2"), deleted.dropLast(1).toSet())
        assertEquals("tournaments/t1", deleted.last())
    }

    private companion object {
        val TOURNAMENTS = """
            {"documents":[
              {"name":"projects/p/databases/(default)/documents/users/uid-1/tournaments/t1",
               "fields":{"type":{"stringValue":"Challenge"},"date":{"timestampValue":"2026-09-07T22:00:00Z"},
                         "participants":{"integerValue":"20"},"registrationFee":{"integerValue":"10"}}},
              {"name":"projects/p/databases/(default)/documents/users/uid-1/tournaments/t2",
               "fields":{"type":{"stringValue":"Cup"},"date":{"timestampValue":"2026-09-20T22:00:00Z"},
                         "participants":{"integerValue":"64"},"registrationFee":{"doubleValue":15.0},
                         "deckName":{"stringValue":"Dragapult"}}},
              {"name":"projects/p/databases/(default)/documents/users/uid-1/tournaments/t3",
               "fields":{"type":{"stringValue":"Local"}}}
            ]}
        """.trimIndent()

        val MATCHES = """
            {"documents":[
              {"name":"projects/p/databases/(default)/documents/users/uid-1/match_logs/m1",
               "fields":{"tournamentId":{"stringValue":"t1"},"round":{"integerValue":"1"},"result":{"stringValue":"W"},
                         "createdAt":{"timestampValue":"2026-09-08T10:00:00Z"}}},
              {"name":"projects/p/databases/(default)/documents/users/uid-1/match_logs/m2",
               "fields":{"tournamentId":{"stringValue":"t1"},"round":{"integerValue":"2"},"result":{"stringValue":"L"},
                         "opponentDeck":{"stringValue":"Charizard ex"},"createdAt":{"timestampValue":"2026-09-08T11:00:00Z"}}}
            ]}
        """.trimIndent()

        val DECKS = """
            {"documents":[
              {"name":"projects/p/databases/(default)/documents/users/uid-1/decks/d1",
               "fields":{"name":{"stringValue":"Dragapult"},"totalCards":{"integerValue":"60"},
                         "cards":{"arrayValue":{"values":[{"stringValue":"c1"},{"stringValue":"c1"}]}}}}
            ]}
        """.trimIndent()
    }
}
