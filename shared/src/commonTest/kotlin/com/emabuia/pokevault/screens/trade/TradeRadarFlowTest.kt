package com.emabuia.pokevault.screens.trade

import androidx.lifecycle.ViewModelStore
import com.emabuia.pokevault.data.AuthRepository
import com.emabuia.pokevault.data.Card
import com.emabuia.pokevault.data.CatalogApi
import com.emabuia.pokevault.data.CatalogRepository
import com.emabuia.pokevault.data.CollectionRepository
import com.emabuia.pokevault.data.CollectionWriter
import com.emabuia.pokevault.data.CollectorRepository
import com.emabuia.pokevault.data.Expansion
import com.emabuia.pokevault.data.FileCache
import com.emabuia.pokevault.data.PriceEntry
import com.emabuia.pokevault.data.WishlistRepository
import com.emabuia.pokevault.data.trade.CoarseLocationSource
import com.emabuia.pokevault.data.trade.Geohash
import com.emabuia.pokevault.data.trade.OverpassClient
import com.emabuia.pokevault.data.trade.TradeApi
import com.emabuia.pokevault.data.trade.TradePrefs
import com.emabuia.pokevault.firebase.FirebaseAuthApi
import com.emabuia.pokevault.firebase.FirestoreApi
import com.emabuia.pokevault.firebase.FirestoreWrites
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TradeRadar su iOS contro un server, un Firestore e un catalogo finti: le
 * parti che qui sono diverse da Android (posizione chiesta dal ViewModel,
 * preferenze su file, riepilogo della collezione col writer di iOS).
 * Gira anche sul simulatore iOS.
 */
class TradeRadarFlowTest {
    private val dir = Path(SystemTemporaryDirectory, "pokevault-trade-${Random.nextLong().toULong()}").toString()
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    /** Le chiamate al server TradeRadar: metodo, percorso e corpo. */
    private val tradeCalls = mutableListOf<Triple<HttpMethod, String, String>>()
    private val commits = mutableListOf<String>()
    private var hasProfile = true
    private var cardsBroken = false

    // Ogni ViewModel del test, chiuso alla fine come quando si esce dalla
    // schermata: senza, il suo lavoro in corso finisce dopo resetMain.
    private val stores = mutableListOf<ViewModelStore>()

    @BeforeTest
    fun main() = Dispatchers.setMain(Dispatchers.Unconfined)

    @AfterTest
    fun cleanUp() {
        stores.forEach { it.clear() }
        Dispatchers.resetMain()
        if (!SystemFileSystem.exists(Path(dir))) return
        // Un file temporaneo della cache puo' sparire fra la lista e la cancellazione.
        SystemFileSystem.list(Path(dir)).forEach { SystemFileSystem.delete(it, mustExist = false) }
        SystemFileSystem.delete(Path(dir), mustExist = false)
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
            path.startsWith("/v1/trade/") -> {
                tradeCalls += Triple(request.method, path, bodyText(request))
                trade(request.method, path.removePrefix("/v1/trade/"))
                    ?.let { (code, body) -> respond(body, code, json) }
                    ?: respond("""{"error":"not_found"}""", HttpStatusCode.NotFound, json)
            }
            path.endsWith("/users/uid-1") -> respond("""{"fields":{"name":{"stringValue":"Ash"}}}""", HttpStatusCode.OK, json)
            path.endsWith(":runQuery") -> respond("[]", HttpStatusCode.OK, json)
            path.endsWith(":commit") -> {
                commits += Json.parseToJsonElement(bodyText(request)).jsonObject["writes"]!!.jsonArray.joinToString { it.toString() }
                respond("{}", HttpStatusCode.OK, json)
            }
            path.endsWith("/users/uid-1/cards/doc-1") -> respond("""{"fields":$PIKACHU_FIELDS}""", HttpStatusCode.OK, json)
            path.endsWith("/users/uid-1/cards") ->
                if (cardsBroken) respond("", HttpStatusCode.InternalServerError)
                else respond("""{"documents":[{"name":"projects/p/databases/(default)/documents/users/uid-1/cards/doc-1","fields":$PIKACHU_FIELDS}]}""", HttpStatusCode.OK, json)
            path.endsWith("/users/uid-1/wishlists") -> respond(
                """{"documents":[{"name":"projects/p/databases/(default)/documents/users/uid-1/wishlists/wl-1","fields":{
                    "name":{"stringValue":"Da prendere"},"cardIds":{"arrayValue":{"values":[{"stringValue":"ita:sv06:67"}]}}}}]}""",
                HttpStatusCode.OK, json,
            )
            path.contains("/users/uid-1/") -> respond("""{"documents":[]}""", HttpStatusCode.OK, json)
            else -> respond("", HttpStatusCode.NotFound)
        }
    }) { expectSuccess = true }

    private fun trade(method: HttpMethod, path: String): Pair<HttpStatusCode, String>? = when {
        path == "profile" && method == HttpMethod.Get ->
            if (hasProfile) HttpStatusCode.OK to PROFILE else HttpStatusCode.NotFound to """{"error":"no_profile"}"""
        path == "profile" && method == HttpMethod.Put -> HttpStatusCode.OK to PROFILE
        path == "haves" && method == HttpMethod.Get -> HttpStatusCode.OK to """{"items":[]}"""
        path in setOf("haves", "wants", "owned") -> HttpStatusCode.OK to "{}"
        path == "matches" -> HttpStatusCode.OK to """{"matches":[],"cards":[]}"""
        path == "proposals" -> HttpStatusCode.OK to PROPOSALS
        else -> null
    }

    /** Pikachu 67 di Caos Nascente in casa, Pikachu 67 di Crepuscolo Mascherato in arrivo. */
    private val catalogApi = object : CatalogApi {
        override suspend fun getExpansions() = listOf(
            Expansion(id = "me04", name = "Caos Nascente", cardCount = 120, officialCount = 99),
            Expansion(id = "sv06", name = "Crepuscolo Mascherato", cardCount = 226, officialCount = 167),
        )
        override suspend fun getExpansionCards(expansionId: String) = cards.filter { it.espansioneId == expansionId }
        override suspend fun getExpansionPrices(expansionId: String) =
            if (expansionId == "sv06") mapOf("67" to PriceEntry(low = 0.5, avg = 0.9)) else emptyMap()
        override suspend fun getFullCatalog() = cards
    }
    private val cards = listOf(
        Card(cardId = "ME04_IT_067.webp", espansioneId = "me04", nome = "Pikachu", tipo = "Lampo", ps = "60", rarity = "Common", stage = "Basic"),
        Card(cardId = "SV06_IT_067.webp", espansioneId = "sv06", nome = "Pikachu", tipo = "Lampo", ps = "70", rarity = "Common", stage = "Basic"),
    )

    private class FakeLocation(var granted: Boolean) : CoarseLocationSource {
        var asked = 0
        override fun permission(): Boolean = granted
        override suspend fun requestPermission(): Boolean {
            asked++
            return granted
        }
        override suspend fun currentPoint(timeoutMs: Long): Pair<Double, Double>? = if (granted) MILANO else null
    }

    private suspend fun viewModel(location: CoarseLocationSource = FakeLocation(granted = true)): TradeRadarViewModel {
        val store = FileCache(dir)
        val auth = AuthRepository(FirebaseAuthApi(client, "k"), FirestoreApi(client, "p"), store, now = { 0L })
        auth.login("ash@gmail.com", "pikachu")
        val firestore = FirestoreApi(client, "p")
        val writes = FirestoreWrites(client, "p")
        val catalog = CatalogRepository(catalogApi)
        val collection = CollectionRepository(firestore, auth, store)
        return TradeRadarViewModel(
            api = TradeApi(client, auth),
            collection = collection,
            writer = CollectionWriter(writes, auth, collection),
            wishlistRepository = WishlistRepository(firestore, auth, catalog, store, writes),
            collector = CollectorRepository(firestore, auth, store, writes),
            catalog = catalog,
            overpass = OverpassClient(client),
            tradePrefs = TradePrefs(store),
            location = location,
        ).also { vm -> stores += ViewModelStore().apply { put("trade-${stores.size}", vm) } }
    }

    private suspend fun waitFor(tries: Int = 200, condition: () -> Boolean) = withContext(Dispatchers.Default) {
        repeat(tries) {
            if (condition()) return@withContext
            delay(15)
        }
    }

    private fun sent(method: HttpMethod, path: String) = tradeCalls.filter { it.first == method && it.second == "/v1/trade/$path" }

    @Test
    fun withoutLocationTheProfileIsNotSent() = runTest {
        hasProfile = false
        val location = FakeLocation(granted = false)
        val vm = viewModel(location)
        waitFor { vm.screen is TradeRadarViewModel.Screen.Onboarding }
        assertIs<TradeRadarViewModel.Screen.Onboarding>(vm.screen)

        vm.activate("Ash", adultConfirmed = true, collectionConsent = true)
        waitFor { vm.notice != null }
        assertEquals(TradeRadarViewModel.Problem.NO_LOCATION, vm.notice)
        assertEquals(1, location.asked)
        assertTrue(sent(HttpMethod.Put, "profile").isEmpty())
        assertFalse(vm.busy)
    }

    @Test
    fun activationSendsTheZoneAndTheCollection() = runTest {
        hasProfile = false
        val vm = viewModel()
        waitFor { vm.screen is TradeRadarViewModel.Screen.Onboarding }

        vm.activate("Ash", adultConfirmed = true, collectionConsent = true)
        waitFor { vm.screen is TradeRadarViewModel.Screen.Ready && sent(HttpMethod.Put, "haves").isNotEmpty() }
        assertIs<TradeRadarViewModel.Screen.Ready>(vm.screen)

        // Solo la cella di 5 caratteri, mai le coordinate.
        val profile = sent(HttpMethod.Put, "profile").single().third
        assertTrue(profile.contains("\"geohash5\":\"${Geohash.encode(MILANO.first, MILANO.second)}\""), profile)
        assertFalse(profile.contains("45.46"), profile)

        assertTrue(sent(HttpMethod.Put, "owned").single().third.contains("me04:67"))
        // Tre copie: due offribili.
        assertEquals(2, vm.duplicates.single().spare)
        assertEquals("me04:67", vm.duplicates.single().key)
        assertNull(vm.notice)
    }

    @Test
    fun anUnreadableCollectionIsNotSentEmpty() = runTest {
        cardsBroken = true
        val vm = viewModel()
        waitFor { vm.notice != null }
        assertEquals(TradeRadarViewModel.Problem.UNAVAILABLE, vm.notice)
        assertTrue(sent(HttpMethod.Put, "owned").isEmpty())
        assertTrue(sent(HttpMethod.Put, "haves").isEmpty())
    }

    @Test
    fun theClosingUpdatesCollectionAndWishlistOnce() = runTest {
        val vm = viewModel()
        waitFor { vm.proposalsLoaded }
        val done = vm.proposals.single()
        assertTrue(vm.needsCollectionUpdate(done))
        assertEquals(1, vm.proposalsToAnswer)

        vm.openClosing(done)
        waitFor { vm.closing?.loading == false }
        val lines = vm.closing!!.lines
        assertEquals(listOf("doc-1"), lines.first { it.giving }.docIds)
        assertTrue(lines.first { !it.giving }.inWishlist)

        commits.clear()
        vm.applyClosing()
        waitFor { vm.info == TradeRadarViewModel.Info.COLLECTION_UPDATED }
        assertNull(vm.closing)

        // Una copia data: doc-1 passa da 3 a 2.
        assertTrue(commits.any { it.contains("cards/doc-1") && it.contains("\"integerValue\":\"2\"") }, commits.joinToString("\n"))
        // Quella ricevuta entra col prezzo minimo dello snapshot e il nome del set italiano.
        val added = commits.single { it.contains("\"ita:sv06:67\"") && it.contains("estimatedValue") }
        assertTrue(added.contains("0.5"), added)
        assertTrue(added.contains("Crepuscolo Mascherato"), added)
        // Ed esce dalla wishlist.
        assertTrue(commits.any { it.contains("wishlists/wl-1") && it.contains("removeAllFromArray") }, commits.joinToString("\n"))

        // Fatto una volta per tutte: anche riaprendo l'app.
        assertFalse(vm.needsCollectionUpdate(done))
        assertTrue("p1" in TradePrefs(FileCache(dir)).read().appliedClosings)
        val reopened = viewModel()
        waitFor { reopened.proposalsLoaded }
        assertFalse(reopened.needsCollectionUpdate(done))
    }

    @Test
    fun introAndTierAreRemembered() = runTest {
        val vm = viewModel()
        waitFor { vm.proposalsLoaded }
        assertFalse(vm.introSeen)
        vm.markIntroSeen()
        vm.checkTier("silver")
        // Il primo livello visto non e' una festa.
        assertNull(vm.celebration)

        val again = viewModel()
        waitFor { again.proposalsLoaded }
        assertTrue(again.introSeen)
        again.checkTier("silver")
        assertNull(again.celebration)
        again.checkTier("gold")
        assertEquals(TradeRadarViewModel.Celebration.TierUp("gold"), again.celebration)
    }

    private companion object {
        val MILANO = 45.4642 to 9.19

        const val PIKACHU_FIELDS = """{
            "name":{"stringValue":"Pikachu"},"set":{"stringValue":"Caos Nascente"},"cardNumber":{"stringValue":"67"},
            "apiCardId":{"stringValue":"ita:me04:67"},"variant":{"stringValue":"Normal"},"condition":{"stringValue":"Near Mint"},
            "language":{"stringValue":"Italiano"},"quantity":{"integerValue":"3"},"estimatedValue":{"doubleValue":0.2},
            "deckOnly":{"booleanValue":false}}"""

        const val PROFILE = """{"nickname":"Ash","geohash5":"u0nd9","paused":false,"ownedHash":"vecchia"}"""

        const val PROPOSALS = """{"proposals":[{"id":"p1","status":"done","counterpart":{"id":"u2","nickname":"Misty"},
            "give":[{"key":"me04:67","variant":"Normal","condition":"Near Mint","language":"Italiano","qty":1}],
            "take":[{"key":"sv06:67","variant":"Normal","condition":"Near Mint","language":"Italiano","qty":1,"name":"Pikachu","setName":"Crepuscolo Mascherato"}]}]}"""
    }
}
