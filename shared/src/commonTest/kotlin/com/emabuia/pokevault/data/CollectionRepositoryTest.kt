package com.emabuia.pokevault.data

import com.emabuia.pokevault.firebase.FirebaseAuthApi
import com.emabuia.pokevault.firebase.FirestoreApi
import com.emabuia.pokevault.util.ImageUrlUtils
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
import kotlin.test.assertTrue

class CollectionRepositoryTest {
    private val dir = Path(SystemTemporaryDirectory, "pokevault-coll-${Random.nextLong().toULong()}").toString()
    private var clock = 1_000_000L
    private var online = true
    private var page2 = PAGE_2
    private val pageRequests = mutableListOf<String?>()

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
            path.endsWith("/users/uid-1") -> respond(
                """{"fields":{"name":{"stringValue":"Ash"}}}""", HttpStatusCode.OK, json,
            )
            path.endsWith("/users/uid-1/cards") -> {
                if (!online) error("offline")
                val token = request.url.parameters["pageToken"]
                pageRequests += token
                // Due pagine, come quando la collezione supera pageSize.
                if (token == null) respond(PAGE_1, HttpStatusCode.OK, json) else respond(page2, HttpStatusCode.OK, json)
            }
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
    private fun repository() = CollectionRepository(FirestoreApi(client, projectId = "p"), auth, store)

    @Test
    fun readsEveryPageAndDropsDeckOnlyCards() = runTest {
        auth.login("ash@gmail.com", "pikachu")
        val cards = repository().load()

        assertEquals(listOf(null, "pagina-2"), pageRequests)
        assertEquals(listOf("Pikachu", "Pikachu", "Charizard ex"), cards.map { it.name })

        val reverse = cards.single { it.variant == "Reverse" }
        assertEquals("doc-2", reverse.id)
        assertEquals(2, reverse.quantity)
        assertEquals(0.5, reverse.estimatedValue)
        assertEquals(1_726_000_000L, reverse.addedAt?.seconds)
        assertEquals(listOf("Basic"), reverse.subtypes)
        assertTrue(cards.single { it.name == "Charizard ex" }.isGraded)
    }

    @Test
    fun uniqueCountsCardsNotPrintsLikeAndroid() = runTest {
        auth.login("ash@gmail.com", "pikachu")
        val stats = CollectionRepository.stats(repository().load())
        // Pikachu Normale x1 + Reverse x2, Charizard x1.
        assertEquals(4, stats.totalCards)
        assertEquals(2, stats.uniqueCards)
        assertEquals(0.2 + 2 * 0.5 + 80.0, stats.totalValue, 0.0001)
    }

    @Test
    fun offlineShowsTheLastDownloadedCollection() = runTest {
        auth.login("ash@gmail.com", "pikachu")
        repository().load()

        online = false
        assertEquals(3, repository().load().size)
        assertEquals(3, repository().cached("uid-1")?.size)
    }

    @Test
    fun oddOldDocumentsAreReadOrCountedNeverHidden() = runTest {
        page2 = """
            {"documents":[
              {"name":"x/cards/doc-5","fields":{"name":{"stringValue":"Snorlax"},"hp":{"stringValue":"150"}}},
              {"name":"x/cards/doc-6","fields":{"name":{"stringValue":"Rotta"},"quantity":{"mapValue":{"fields":{}}}}}
            ]}
        """
        auth.login("ash@gmail.com", "pikachu")
        val repository = repository()
        val cards = repository.load()
        assertEquals(150, cards.single { it.name == "Snorlax" }.hp)
        assertEquals(1, repository.unreadable)
    }

    @Test
    fun pokeWalletImagesGoThroughTheWorker() {
        assertEquals(
            "https://worker.dev/images/abc%20def.png?size=low",
            ImageUrlUtils.safeImageUrl(ImageUrlUtils.proxyPokeWalletUrl("https://api.pokewallet.io/images/abc def.png?size=low", "https://worker.dev/")),
        )
        // Gli altri indirizzi restano come sono.
        assertEquals("https://x.dev/images/it/ME05/1", ImageUrlUtils.proxyPokeWalletUrl("https://x.dev/images/it/ME05/1", "https://worker.dev"))
    }

    private companion object {
        // Come li scrive FirestoreRepository su Android: interi come stringa,
        // Timestamp, liste, isGraded col suo nome.
        const val PAGE_1 = """
            {"documents":[
              {"name":"projects/p/databases/(default)/documents/users/uid-1/cards/doc-1","fields":{
                "name":{"stringValue":"Pikachu"},"set":{"stringValue":"Evoluzioni a Paldea"},"cardNumber":{"stringValue":"063"},
                "variant":{"stringValue":"Normal"},"quantity":{"integerValue":"1"},"estimatedValue":{"doubleValue":0.2},
                "rarity":{"stringValue":"Common"},"deckOnly":{"booleanValue":false}}},
              {"name":"projects/p/databases/(default)/documents/users/uid-1/cards/doc-2","fields":{
                "name":{"stringValue":"Pikachu"},"set":{"stringValue":"Evoluzioni a Paldea"},"cardNumber":{"stringValue":"63"},
                "variant":{"stringValue":"Reverse"},"quantity":{"integerValue":"2"},"estimatedValue":{"doubleValue":0.5},
                "addedAt":{"timestampValue":"2024-09-10T20:26:40.123Z"},
                "subtypes":{"arrayValue":{"values":[{"stringValue":"Basic"}]}},"grade":{"nullValue":null}}}
            ],"nextPageToken":"pagina-2"}
        """
        const val PAGE_2 = """
            {"documents":[
              {"name":"projects/p/databases/(default)/documents/users/uid-1/cards/doc-3","fields":{
                "name":{"stringValue":"Charizard ex"},"set":{"stringValue":"Ossidiana Infuocata"},"cardNumber":{"stringValue":"125"},
                "quantity":{"integerValue":"1"},"estimatedValue":{"integerValue":"80"},"isGraded":{"booleanValue":true}}},
              {"name":"projects/p/databases/(default)/documents/users/uid-1/cards/doc-4","fields":{
                "name":{"stringValue":"Carta del deck di prova"},"deckOnly":{"booleanValue":true}}}
            ]}
        """
    }
}
