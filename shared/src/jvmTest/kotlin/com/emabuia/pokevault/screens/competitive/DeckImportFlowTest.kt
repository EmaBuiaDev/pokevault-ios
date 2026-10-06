package com.emabuia.pokevault.screens.competitive

import androidx.lifecycle.ViewModelStore
import com.emabuia.pokevault.data.AuthRepository
import com.emabuia.pokevault.data.Card
import com.emabuia.pokevault.data.CatalogApi
import com.emabuia.pokevault.data.CatalogRepository
import com.emabuia.pokevault.data.CollectionRepository
import com.emabuia.pokevault.data.CollectionWriter
import com.emabuia.pokevault.data.CompetitiveRepository
import com.emabuia.pokevault.data.Expansion
import com.emabuia.pokevault.data.FileCache
import com.emabuia.pokevault.data.PremiumRepository
import com.emabuia.pokevault.data.PriceEntry
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Un import vero dall'inizio alla fine, contro un Firestore e un catalogo
 * finti: le carte possedute entrano subito, le mancanti si cercano nel
 * catalogo, quelle che il catalogo non ha diventano segnaposto.
 */
class DeckImportFlowTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val created = CopyOnWriteArrayList<JsonObject>()

    // Ogni ViewModel del test, chiuso alla fine come quando si esce dalla
    // schermata: smette di partire lavoro nuovo quando il test e' finito.
    private val stores = mutableListOf<ViewModelStore>()

    @BeforeTest
    fun mainDispatcher() = Dispatchers.setMain(Dispatchers.Unconfined)

    @AfterTest
    fun reset() {
        stores.forEach { it.clear() }
        // Niente resetMain: un lavoro gia' partito su un altro thread (withContext)
        // torna comunque sul Main per chiudersi, anche a ViewModel cancellato. Senza
        // Main di prova lancia, e la CI addebita l'errore al test dopo
        // (UncaughtExceptionsBeforeTest, CI di f969c28, f0f27e0 e 49fa0f3). Unconfined
        // resta impostato: chi riprende si chiude li', e ogni test lo reimposta.
    }

    private fun bodyText(request: HttpRequestData): String = when (val body = request.body) {
        is TextContent -> body.text
        is OutgoingContent.ByteArrayContent -> body.bytes().decodeToString()
        else -> ""
    }

    /** Due Dreepy posseduti, nient'altro. */
    private val ownedCards = """{"documents":[{"name":"x/cards/own-dreepy","fields":{"name":{"stringValue":"Dreepy"},
        "quantity":{"integerValue":"2"},"apiCardId":{"stringValue":"ita:sv06:128"},"cardNumber":{"stringValue":"128"},
        "set":{"stringValue":"Crepuscolo Mascherato"},"hp":{"integerValue":"70"}}}]}"""

    private val client = HttpClient(MockEngine { request ->
        val path = request.url.encodedPath
        when {
            path.endsWith("accounts:signInWithPassword") -> respond(
                """{"localId":"uid-1","email":"ash@gmail.com","idToken":"id-1","refreshToken":"r-1","expiresIn":"3600"}""",
                HttpStatusCode.OK, json,
            )
            path.endsWith("/users/uid-1") -> respond("""{"fields":{"name":{"stringValue":"Ash"}}}""", HttpStatusCode.OK, json)
            path.endsWith("/users/uid-1/cards") -> respond(ownedCards, HttpStatusCode.OK, json)
            path.endsWith("/users/uid-1/decks") -> respond("""{"documents":[]}""", HttpStatusCode.OK, json)
            path.endsWith(":runQuery") -> respond("[]", HttpStatusCode.OK, json)
            path.endsWith(":commit") -> {
                Json.parseToJsonElement(bodyText(request)).jsonObject["writes"]!!.jsonArray
                    .map { it.jsonObject }.filter { "update" in it }.forEach { created += it }
                respond("{}", HttpStatusCode.OK, json)
            }
            path.endsWith("/entitlement") -> respond("""{"entitled":true}""", HttpStatusCode.OK, json)
            path.endsWith("/gift/me") -> respond("{}", HttpStatusCode.OK, json)
            else -> respond("", HttpStatusCode.NotFound)
        }
    }) { expectSuccess = true }

    /** Il catalogo italiano finto: Drakloak (TWM 129) e un'Energia Psico base. */
    private val catalogApi = object : CatalogApi {
        override suspend fun getExpansions() = listOf(
            Expansion(id = "sv06", name = "Crepuscolo Mascherato"),
            Expansion(id = "sve", name = "Energie base"),
        )
        override suspend fun getExpansionCards(expansionId: String) = fullCatalog.filter { it.espansioneId == expansionId }
        override suspend fun getExpansionPrices(expansionId: String) = mapOf("129" to PriceEntry(low = 0.2))
        override suspend fun getFullCatalog() = fullCatalog
    }
    private val fullCatalog = listOf(
        Card(cardId = "SV06_IT_129.webp", espansioneId = "sv06", nome = "Drakloak", tipo = "Drago", ps = "90", stage = "Stage1"),
        Card(cardId = "SV06_IT_128.webp", espansioneId = "sv06", nome = "Dreepy", tipo = "Drago", ps = "70", stage = "Basic"),
        Card(cardId = "SVE_IT_005.webp", espansioneId = "sve", nome = "Energia Psico base"),
    )

    private fun viewModel(): DeckLabViewModel {
        val store = FileCache(File(System.getProperty("java.io.tmpdir"), "pv-import-${System.nanoTime()}").path)
        val auth = AuthRepository(FirebaseAuthApi(client, "k"), FirestoreApi(client, "p"), store, now = { 0L })
        runBlocking { auth.login("ash@gmail.com", "pikachu") }
        val collection = CollectionRepository(FirestoreApi(client, "p"), auth, store)
        val vm = DeckLabViewModel(
            CompetitiveRepository(FirestoreApi(client, "p"), auth, store, FirestoreWrites(client, "p")),
            collection,
            PremiumRepository(auth, client, now = { 0L }, baseUrl = "https://w"),
            CatalogRepository(catalogApi),
            CollectionWriter(FirestoreWrites(client, "p"), auth, collection),
        )
        stores += ViewModelStore().apply { put("deck-${stores.size}", vm) }
        repeat(200) { if (vm.allCards.isEmpty()) Thread.sleep(10) }
        created.clear()
        return vm
    }

    private val decklist = """
        Pokémon: 6
        4 Dreepy TWM 128
        2 Drakloak TWM 129

        Energy: 3
        3 Basic Psychic Energy SVE 5

        Trainer: 1
        1 Carta Inventata XYZ 999
    """.trimIndent()

    private fun waitUntil(condition: () -> Boolean) {
        repeat(300) { if (!condition()) Thread.sleep(10) }
    }

    @Test
    fun ownedCardsGoInAtOnceAndTheMissingOnesWaitForTheChoice() {
        val vm = viewModel()
        val result = vm.importFromText(decklist)

        // I due Dreepy posseduti: dentro subito, gli altri due mancano.
        assertEquals(listOf("own-dreepy", "own-dreepy"), vm.selectedCardsIds)
        assertEquals(10, result.totalRequested)
        assertEquals(listOf(2, 2, 3, 1), result.missingMetaDeckCards.map { it.qty })
        assertTrue(vm.isImportSourceChoicePending)
        assertTrue(vm.isImportReviewMode)
    }

    @Test
    fun aTestDeckGetsCatalogCardsAndAPlaceholderAllOutsideTheCollection() {
        val vm = viewModel()
        vm.importFromText(decklist)
        vm.applyImportCardSource(DeckLabViewModel.DeckCardSource.DECK_ONLY)
        waitUntil { !vm.isImportSourceChoicePending }

        assertFalse(vm.isAddingMissingCards)
        assertEquals(10, vm.selectedCardsIds.size)
        // Quattro documenti nuovi: Dreepy e Drakloak dal catalogo, l'energia, il segnaposto.
        val names = created.map { it["update"]!!.jsonObject["fields"]!!.jsonObject["name"]!!.jsonObject["stringValue"]!!.jsonPrimitive.content }
        assertEquals(listOf("Dreepy", "Drakloak", "Energia Psico base", "Carta Inventata"), names)
        assertTrue(created.all { it["update"]!!.jsonObject["fields"]!!.jsonObject["deckOnly"]!!.jsonObject["booleanValue"]!!.jsonPrimitive.content == "true" })
        assertEquals(listOf("Carta Inventata"), vm.importPlaceholderNames)
        assertTrue(vm.editingDeckHasDeckOnlyCards)
        // Il riepilogo resta, senza piu' mancanti.
        assertEquals(0, vm.importResult?.missing)
    }
}
