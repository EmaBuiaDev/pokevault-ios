package com.emabuia.pokevault.data

import com.emabuia.pokevault.data.model.WishlistDraft
import com.emabuia.pokevault.firebase.FirebaseAuthApi
import com.emabuia.pokevault.firebase.FirestoreApi
import com.emabuia.pokevault.firebase.FirestoreWrites
import com.emabuia.pokevault.screens.wishlist.WishlistViewModel
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Le scritture sulle wishlist e la lettura del Premium contro server finti:
 * si guarda cosa partirebbe, prima di toccare le liste vere di qualcuno.
 */
class WishlistWritesTest {
    private val dir = Path(SystemTemporaryDirectory, "pokevault-wishw-${Random.nextLong().toULong()}").toString()
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private val commits = mutableListOf<JsonObject>()
    /** Le liste su cui il commit risponde errore (documento sparito). */
    private val brokenLists = mutableSetOf<String>()
    private var entitlement: Pair<HttpStatusCode, String> = HttpStatusCode.OK to """{"entitled":false,"state":"none"}"""
    private var gift: Pair<HttpStatusCode, String> = HttpStatusCode.OK to """{"code":"AMICO1"}"""

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
            path.endsWith(":commit") -> {
                val write = Json.parseToJsonElement(bodyText(request)).jsonObject["writes"]!!.jsonArray.single().jsonObject
                val target = write["transform"]?.jsonObject?.get("document")?.jsonPrimitive?.content.orEmpty()
                if (brokenLists.any { target.endsWith("/wishlists/$it") }) {
                    respond("""{"error":{"code":404}}""", HttpStatusCode.NotFound, json)
                } else {
                    commits += write
                    respond("{}", HttpStatusCode.OK, json)
                }
            }
            path.endsWith("/v1/billing/entitlement") -> {
                assertEquals("Bearer id-1", request.headers[HttpHeaders.Authorization])
                respond(entitlement.second, entitlement.first, json)
            }
            path.endsWith("/v1/gift/me") -> respond(gift.second, gift.first, json)
            else -> respond("", HttpStatusCode.NotFound)
        }
    }) { expectSuccess = true }

    private suspend fun signedIn(): AuthRepository {
        val auth = AuthRepository(FirebaseAuthApi(client, "k"), FirestoreApi(client, "p"), FileCache(dir), now = { 0L })
        auth.login("ash@gmail.com", "pikachu")
        return auth
    }

    private suspend fun repository(): WishlistRepository {
        val store = FileCache(dir)
        val catalog = object : CatalogApi {
            override suspend fun getExpansions() = emptyList<Expansion>()
            override suspend fun getExpansionCards(expansionId: String) = emptyList<Card>()
            override suspend fun getExpansionPrices(expansionId: String) = emptyMap<String, PriceEntry>()
            override suspend fun getFullCatalog() = emptyList<Card>()
        }
        return WishlistRepository(FirestoreApi(client, "p"), signedIn(), CatalogRepository(catalog, store), store, FirestoreWrites(client, "p"))
    }

    private fun JsonObject.field(name: String) = this["update"]!!.jsonObject["fields"]!!.jsonObject[name]!!.jsonObject

    @Test
    fun aNewListHasAndroidsFieldsAndCanStartWithTheCard() = runTest {
        val repo = repository()
        val id = repo.create(WishlistDraft(name = "Natale", iconKey = "gift", accentKey = "red", budgetEur = 30.0), firstCardIds = listOf("ita:me05:4"))

        val write = commits.single()
        assertTrue(write["update"]!!.jsonObject["name"]!!.jsonPrimitive.content.endsWith("/users/uid-1/wishlists/$id"))
        assertEquals("Natale", write.field("name")["stringValue"]!!.jsonPrimitive.content)
        assertEquals("gift", write.field("iconKey")["stringValue"]!!.jsonPrimitive.content)
        assertEquals("red", write.field("accentKey")["stringValue"]!!.jsonPrimitive.content)
        assertEquals(30.0, write.field("budgetEur")["doubleValue"]!!.jsonPrimitive.content.toDouble())
        assertEquals("ita:me05:4", write.field("cardIds")["arrayValue"]!!.jsonObject["values"]!!.jsonArray.single().jsonObject["stringValue"]!!.jsonPrimitive.content)
        assertTrue("timestampValue" in write.field("createdAt"))
        // Mai sovrascrivere una lista che esiste gia'.
        assertEquals("false", write["currentDocument"]!!.jsonObject["exists"]!!.jsonPrimitive.content)
    }

    @Test
    fun editingTouchesOnlyTheDetailsNeverTheCards() = runTest {
        repository().update("w1", WishlistDraft(name = "Regali", iconKey = "gift", accentKey = "red", budgetEur = 0.0))

        val mask = commits.single()["updateMask"]!!.jsonObject["fieldPaths"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("name", "iconKey", "accentKey", "budgetEur"), mask)
        assertFalse("cardIds" in commits.single()["update"]!!.jsonObject["fields"]!!.jsonObject)
    }

    @Test
    fun deletingRemovesOnlyThatList() = runTest {
        repository().delete("w1")
        assertTrue(commits.single()["delete"]!!.jsonPrimitive.content.endsWith("/users/uid-1/wishlists/w1"))
    }

    @Test
    fun movingACardAddsAndRemovesWithoutRewritingTheLists() = runTest {
        repository().moveCard("ita:me05:4", addTo = listOf("w2"), removeFrom = listOf("w1"))

        val byList = commits.associate {
            val transform = it["transform"]!!.jsonObject
            transform["document"]!!.jsonPrimitive.content.substringAfterLast('/') to
                transform["fieldTransforms"]!!.jsonArray.single().jsonObject
        }
        assertTrue("appendMissingElements" in byList.getValue("w2"))
        assertTrue("removeAllFromArray" in byList.getValue("w1"))
        assertEquals("cardIds", byList.getValue("w1")["fieldPath"]!!.jsonPrimitive.content)
    }

    @Test
    fun aListThatFailsDoesNotStopTheOthersButIsReported() = runTest {
        brokenLists += "w1"
        val repo = repository()
        assertFailsWith<IllegalStateException> {
            repo.moveCard("ita:me05:4", addTo = listOf("w1", "w2"), removeFrom = emptyList())
        }
        // w2 e' stata scritta lo stesso, e le schermate rileggono.
        assertEquals(1, commits.size)
        assertEquals(1, repo.changes.value)
    }

    @Test
    fun boughtOnAndroidIsPremium() = runTest {
        entitlement = HttpStatusCode.OK to """{"entitled":true,"state":"active"}"""
        assertEquals(true, PremiumRepository(signedIn(), client, now = { 1_000L }).isPremium())
    }

    @Test
    fun aGiftCountsOnlyUntilItExpires() = runTest {
        gift = HttpStatusCode.OK to """{"code":"AMICO1","giftUntilMs":5000}"""
        val auth = signedIn()
        assertEquals(true, PremiumRepository(auth, client, now = { 4_999L }).isPremium())
        assertEquals(false, PremiumRepository(auth, client, now = { 5_000L }).isPremium())
    }

    @Test
    fun aServerThatDoesNotAnswerIsUnknownNotFree() = runTest {
        gift = HttpStatusCode.InternalServerError to "{}"
        assertNull(PremiumRepository(signedIn(), client, now = { 0L }).isPremium())
    }

    @Test
    fun namesAndBudgetsAreReadLikeOnAndroid() {
        assertTrue(WishlistViewModel.isValidWishlistName(" Natale "))
        assertFalse(WishlistViewModel.isValidWishlistName("   "))
        assertFalse(WishlistViewModel.isValidWishlistName("x".repeat(41)))
        assertEquals(30.5, WishlistViewModel.parseBudget("30,50 €"))
        assertEquals(0.0, WishlistViewModel.parseBudget("-4"))
    }
}
