package com.emabuia.pokevault.data

import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.firebase.FirebaseAuthApi
import com.emabuia.pokevault.firebase.FirestoreApi
import com.emabuia.pokevault.firebase.FirestoreWrites
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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Le scritture sulla collezione contro un Firestore finto: si controlla
 * esattamente cosa verrebbe mandato, campo per campo, prima di toccare i dati
 * veri di qualcuno.
 */
class CollectionWriterTest {
    private val dir = Path(SystemTemporaryDirectory, "pokevault-writer-${Random.nextLong().toULong()}").toString()
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    /** Le scritture mandate a :commit, una per richiesta. */
    private val commits = mutableListOf<JsonObject>()
    private val queries = mutableListOf<String>()
    /** Cosa risponde la query delle stampe gia' possedute. */
    private var existingPrints = "[]"

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
            path.endsWith(":runQuery") -> { queries += bodyText(request); respond(existingPrints, HttpStatusCode.OK, json) }
            path.endsWith(":commit") -> {
                commits += Json.parseToJsonElement(bodyText(request)).jsonObject["writes"]!!.jsonArray.single().jsonObject
                respond("{}", HttpStatusCode.OK, json)
            }
            path.endsWith("/users/uid-1/cards") -> respond("""{"documents":[]}""", HttpStatusCode.OK, json)
            else -> respond("", HttpStatusCode.NotFound)
        }
    }) { expectSuccess = true }

    private suspend fun writer(): CollectionWriter {
        val store = FileCache(dir)
        val auth = AuthRepository(FirebaseAuthApi(client, "k"), FirestoreApi(client, "p"), store, now = { 0L })
        auth.login("ash@gmail.com", "pikachu")
        commits.clear() // il profilo letto all'accesso non conta
        return CollectionWriter(FirestoreWrites(client, "p"), auth, CollectionRepository(FirestoreApi(client, "p"), auth, store))
    }

    private val lurantis = Card(
        cardId = "ME05_IT_004.webp", espansioneId = "me05", nome = "Lurantis-ex", tipo = "Erba", ps = "260",
        rarity = "Double Rare", stage = "Stage1",
    )

    private fun JsonObject.field(name: String): JsonObject = this["update"]!!.jsonObject["fields"]!!.jsonObject[name]!!.jsonObject
    private fun JsonObject.str(name: String) = field(name)["stringValue"]?.jsonPrimitive?.content

    @Test
    fun aNewCardIsTheSameDocumentAndroidWrites() = runTest {
        writer().addFromCatalog(lurantis, "Buio Pesto", PriceEntry(low = 2.5, avg = 3.0), "Holo", 2, "Near Mint", "🇮🇹 Italiano")

        // Prima si cerca la stessa stampa: apiCardId e variante, come la query di Android.
        assertTrue(""""stringValue":"ita:me05:4"""" in queries.single() && """"stringValue":"Holo"""" in queries.single())

        val create = commits[0]
        val name = create["update"]!!.jsonObject["name"]!!.jsonPrimitive.content
        assertTrue(name.startsWith("projects/p/databases/(default)/documents/users/uid-1/cards/"))
        assertEquals(20, name.substringAfterLast('/').length)
        assertEquals("false", create["currentDocument"]!!.jsonObject["exists"]!!.jsonPrimitive.content)

        assertEquals("Lurantis-ex", create.str("name"))
        assertEquals("Buio Pesto", create.str("set"))
        assertEquals("https://pokevault-proxy.pokevault-emanu.workers.dev/images/it/ME05/4?size=low&itv=r2v3", create.str("imageUrl"))
        assertEquals("Erba", create.str("type"))
        assertEquals("260", create.field("hp")["integerValue"]!!.jsonPrimitive.content)
        assertEquals("Pokémon", create.str("supertype"))
        assertEquals("Stage1", create.field("subtypes")["arrayValue"]!!.jsonObject["values"]!!.jsonArray.single().jsonObject["stringValue"]!!.jsonPrimitive.content)
        assertEquals("2.5", create.field("estimatedValue")["doubleValue"]!!.jsonPrimitive.content)
        assertEquals("2", create.field("quantity")["integerValue"]!!.jsonPrimitive.content)
        assertEquals("ita:me05:4", create.str("apiCardId"))
        assertEquals("4", create.str("cardNumber"))
        assertEquals("Holo", create.str("variant"))
        assertEquals("🇮🇹 Italiano", create.str("language"))
        assertEquals("false", create.field("deckOnly")["booleanValue"]!!.jsonPrimitive.content)
        assertTrue(create.field("addedAt").containsKey("timestampValue"))

        // Poi i totali dell'utente: +2 carte, +5 euro.
        val totals = commits[1]["transform"]!!.jsonObject["fieldTransforms"]!!.jsonArray
        assertEquals("2", totals[0].jsonObject["increment"]!!.jsonObject["integerValue"]!!.jsonPrimitive.content)
        assertEquals("5.0", totals[1].jsonObject["increment"]!!.jsonObject["doubleValue"]!!.jsonPrimitive.content)
    }

    @Test
    fun theSamePrintInTheSameLanguageGrowsInsteadOfDuplicating() = runTest {
        existingPrints = """[{"document":{"name":"x/cards/doc-holo","fields":{
            "quantity":{"integerValue":"3"},"estimatedValue":{"doubleValue":2.0},"language":{"stringValue":"Italiano"},
            "supertype":{"stringValue":"Pokémon"},"type":{"stringValue":"Erba"},"hp":{"integerValue":"260"},
            "subtypes":{"arrayValue":{"values":[{"stringValue":"Stage1"}]}}}}}]"""
        writer().addFromCatalog(lurantis, "Buio Pesto", PriceEntry(low = 2.5), "Holo", 1, "Near Mint", "🇮🇹 Italiano")

        val update = commits[0]
        assertTrue(update["update"]!!.jsonObject["name"]!!.jsonPrimitive.content.endsWith("/cards/doc-holo"))
        assertEquals("4", update.field("quantity")["integerValue"]!!.jsonPrimitive.content)
        val mask = update["updateMask"]!!.jsonObject["fieldPaths"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()
        // Solo quantita', prezzo e lingua: il documento era gia' classificato.
        assertEquals(setOf("quantity", "estimatedValue", "language"), mask)
    }

    @Test
    fun anotherLanguageOrADeckOnlyCopyIsNeverMerged() = runTest {
        existingPrints = """[
            {"document":{"name":"x/cards/doc-eng","fields":{"quantity":{"integerValue":"1"},"language":{"stringValue":"🇬🇧 English"}}}},
            {"document":{"name":"x/cards/doc-deck","fields":{"quantity":{"integerValue":"1"},"language":{"stringValue":"🇮🇹 Italiano"},"deckOnly":{"booleanValue":true}}}}
        ]"""
        writer().addFromCatalog(lurantis, "Buio Pesto", null, "Holo", 1, "Near Mint", "🇮🇹 Italiano")
        // Un documento nuovo, non un aggiornamento di quelli esistenti.
        assertEquals("false", commits[0]["currentDocument"]!!.jsonObject["exists"]!!.jsonPrimitive.content)
    }

    @Test
    fun changingCopiesAndDeletingMoveTheTotalsLikeAndroid() = runTest {
        val writer = writer()
        val print = PokemonCard(id = "doc-1", name = "Pikachu", quantity = 3, estimatedValue = 0.5)

        writer.setQuantity(print, 5)
        assertEquals(listOf("quantity"), commits[0]["updateMask"]!!.jsonObject["fieldPaths"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("2", commits[1]["transform"]!!.jsonObject["fieldTransforms"]!!.jsonArray[0].jsonObject["increment"]!!.jsonObject["integerValue"]!!.jsonPrimitive.content)

        commits.clear()
        writer.deletePrint(print)
        assertTrue(commits[0]["delete"]!!.jsonPrimitive.content.endsWith("/cards/doc-1"))
        assertEquals("-3", commits[1]["transform"]!!.jsonObject["fieldTransforms"]!!.jsonArray[0].jsonObject["increment"]!!.jsonObject["integerValue"]!!.jsonPrimitive.content)

        // Una carta solo-deck non ha mai mosso i totali: non li muove uscendo.
        commits.clear()
        writer.deletePrint(print.copy(deckOnly = true))
        assertEquals(1, commits.size)
    }

    @Test
    fun theTrashRemovesEveryPrintButLeavesDeckOnlyCopies() = runTest {
        existingPrints = """[
            {"document":{"name":"x/cards/doc-holo","fields":{"quantity":{"integerValue":"2"},"estimatedValue":{"doubleValue":1.0}}}},
            {"document":{"name":"x/cards/doc-rev","fields":{"quantity":{"integerValue":"1"}}}},
            {"document":{"name":"x/cards/doc-deck","fields":{"quantity":{"integerValue":"1"},"deckOnly":{"booleanValue":true}}}}
        ]"""
        writer().deleteAllPrints("ita:me05:4")
        val deleted = commits.mapNotNull { it["delete"]?.jsonPrimitive?.content?.substringAfterLast('/') }
        assertEquals(listOf("doc-holo", "doc-rev"), deleted)
    }

    @Test
    fun aGradedPrintWritesOnlyTheGradingFields() = runTest {
        val print = PokemonCard(id = "doc-holo", name = "Lurantis-ex", quantity = 1)
        writer().setGrading(print, isGraded = true, grade = 9.5f, company = "PSA")

        val update = commits.single()
        assertTrue(update["update"]!!.jsonObject["name"]!!.jsonPrimitive.content.endsWith("/cards/doc-holo"))
        val mask = update["updateMask"]!!.jsonObject["fieldPaths"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("isGraded", "grade", "gradingCompany"), mask)
        assertEquals("true", update.field("isGraded")["booleanValue"]!!.jsonPrimitive.content)
        assertEquals("9.5", update.field("grade")["doubleValue"]!!.jsonPrimitive.content)
        assertEquals("PSA", update.str("gradingCompany"))
    }

    @Test
    fun unmarkingKeepsGradeAndCompanyAndMissingFieldsWriteNothing() = runTest {
        val print = PokemonCard(id = "doc-holo", name = "Lurantis-ex", grade = 9f, gradingCompany = "PSA").also { it.isGraded = true }
        val w = writer()
        w.setGrading(print, isGraded = false, grade = null, company = "")
        // Come su Android: si spegne solo la spunta, voto ed ente restano sul documento.
        assertEquals(listOf("isGraded"), commits.single()["updateMask"]!!.jsonObject["fieldPaths"]!!.jsonArray.map { it.jsonPrimitive.content })

        commits.clear()
        assertFailsWith<IllegalArgumentException> { w.setGrading(print, isGraded = true, grade = null, company = "PSA") }
        assertFailsWith<IllegalArgumentException> { w.setGrading(print, isGraded = true, grade = 9f, company = " ") }
        assertTrue(commits.isEmpty())
    }
}
