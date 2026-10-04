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

class WishlistRepositoryTest {
    private val dir = Path(SystemTemporaryDirectory, "pokevault-wish-${Random.nextLong().toULong()}").toString()

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
                """{"localId":"uid-1","email":"ash@gmail.com","idToken":"id-1","refreshToken":"r-1","expiresIn":"3600"}""",
                HttpStatusCode.OK, json,
            )
            path.endsWith("/users/uid-1") -> respond("""{"fields":{"name":{"stringValue":"Ash"}}}""", HttpStatusCode.OK, json)
            path.endsWith("/users/uid-1/wishlists") -> respond(WISHLISTS, HttpStatusCode.OK, json)
            else -> respond("", HttpStatusCode.NotFound)
        }
    }) { expectSuccess = true }

    /** Il catalogo finto: due carte di Buio Pesto e una galleria, coi prezzi di Buio Pesto. */
    private val catalogApi = object : CatalogApi {
        override suspend fun getExpansions() = emptyList<Expansion>()
        override suspend fun getExpansionCards(expansionId: String) = getFullCatalog().filter { it.espansioneId == expansionId }
        override suspend fun getExpansionPrices(expansionId: String) =
            if (expansionId == "me05") mapOf("4" to PriceEntry(low = 2.0), "25" to PriceEntry(low = 0.5)) else emptyMap()
        override suspend fun getFullCatalog() = listOf(
            Card(cardId = "ME05_IT_004.webp", espansioneId = "me05", nome = "Lurantis-ex"),
            Card(cardId = "ME05_IT_025.webp", espansioneId = "me05", nome = "Pikachu"),
            Card(cardId = "SWSH12TG_IT_TG05.png", espansioneId = "swsh12tg", nome = "Pikachu VMAX"),
        )
    }

    @Test
    fun italianIdsBecomeCardsTheRestIsCountedNotHidden() = runTest {
        val store = FileCache(dir)
        val auth = AuthRepository(FirebaseAuthApi(client, "k"), FirestoreApi(client, "p"), store, now = { 0L })
        auth.login("ash@gmail.com", "pikachu")
        val repository = WishlistRepository(FirestoreApi(client, "p"), auth, CatalogRepository(catalogApi, store), store)

        val lists = repository.wishlists()
        // Dalla piu' recente.
        assertEquals(listOf("Natale", "Da prendere"), lists.map { it.name })

        val content = repository.content(lists.last())
        assertEquals(listOf("Lurantis-ex", "Pikachu VMAX", "Pikachu"), content.cards.map { it.card.nome })
        assertEquals(2, content.unresolved) // un id PokeWallet e uno vecchio
        assertEquals(2.5, content.totalValue, 0.0001) // la galleria non ha prezzo
        assertEquals("€ 2,00", content.cards.first().price?.displayText())
    }

    private companion object {
        const val WISHLISTS = """
            {"documents":[
              {"name":"x/wishlists/w1","fields":{
                "name":{"stringValue":"Da prendere"},"iconKey":{"stringValue":"pokeball"},"budgetEur":{"doubleValue":50},
                "createdAt":{"timestampValue":"2026-01-01T10:00:00Z"},
                "cardIds":{"arrayValue":{"values":[
                  {"stringValue":"ita:me05:4"},{"stringValue":"pk_123abc"},{"stringValue":"ita:swsh12tg:TG05"},
                  {"stringValue":"sv3-125"},{"stringValue":"ita:me05:25"}]}}}},
              {"name":"x/wishlists/w2","fields":{
                "name":{"stringValue":"Natale"},"iconKey":{"stringValue":"gift"},
                "createdAt":{"timestampValue":"2026-09-01T10:00:00Z"},"cardIds":{"arrayValue":{}}}}
            ]}
        """
    }
}
