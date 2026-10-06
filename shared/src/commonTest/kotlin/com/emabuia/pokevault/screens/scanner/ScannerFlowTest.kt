package com.emabuia.pokevault.screens.scanner

import androidx.lifecycle.ViewModelStore
import com.emabuia.pokevault.data.AuthRepository
import com.emabuia.pokevault.data.Card
import com.emabuia.pokevault.data.CatalogApi
import com.emabuia.pokevault.data.CatalogRepository
import com.emabuia.pokevault.data.CollectionRepository
import com.emabuia.pokevault.data.CollectionWriter
import com.emabuia.pokevault.data.Expansion
import com.emabuia.pokevault.data.FileCache
import com.emabuia.pokevault.data.PriceEntry
import com.emabuia.pokevault.firebase.FirebaseAuthApi
import com.emabuia.pokevault.firebase.FirestoreApi
import com.emabuia.pokevault.firebase.FirestoreWrites
import com.emabuia.pokevault.ocr.OCRTextBlock
import com.emabuia.pokevault.ocr.ScannedFrame
import com.emabuia.pokevault.ocr.ZoneBoundingBox
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
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
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Lo Scanner senza camera: entrano i fotogrammi gia' letti (come li produce
 * l'OCR), esce la carta proposta; poi aggiungi e annulla, contro un Firestore
 * e un catalogo finti. Gira anche sul simulatore iOS.
 */
class ScannerFlowTest {
    private val dir = Path(SystemTemporaryDirectory, "pokevault-scan-${Random.nextLong().toULong()}").toString()
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val commits = mutableListOf<JsonObject>()

    // Ogni ViewModel del test, chiuso alla fine come quando si esce dalla
    // schermata: smette di partire lavoro nuovo quando il test e' finito.
    private val stores = mutableListOf<ViewModelStore>()

    @BeforeTest
    fun main() = Dispatchers.setMain(Dispatchers.Unconfined)

    @AfterTest
    fun cleanUp() {
        stores.forEach { it.clear() }
        // Niente resetMain: un lavoro gia' partito su un altro thread (withContext)
        // torna comunque sul Main per chiudersi, anche a ViewModel cancellato. Senza
        // Main di prova lancia, e la CI addebita l'errore al test dopo
        // (UncaughtExceptionsBeforeTest, CI di f969c28, f0f27e0 e 49fa0f3). Unconfined
        // resta impostato: chi riprende si chiude li', e ogni test lo reimposta.
        if (!SystemFileSystem.exists(Path(dir))) return
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
            path.endsWith("/users/uid-1") -> respond("""{"fields":{"name":{"stringValue":"Ash"}}}""", HttpStatusCode.OK, json)
            path.endsWith(":runQuery") -> respond("[]", HttpStatusCode.OK, json)
            path.endsWith(":commit") -> {
                commits += Json.parseToJsonElement(bodyText(request)).jsonObject["writes"]!!.jsonArray.map { it.jsonObject }
                respond("{}", HttpStatusCode.OK, json)
            }
            path.contains("/users/uid-1/cards/") -> respond(
                """{"name":"x","fields":{"quantity":{"integerValue":"1"},"estimatedValue":{"doubleValue":0.5}}}""",
                HttpStatusCode.OK, json,
            )
            path.endsWith("/users/uid-1/cards") -> respond("""{"documents":[]}""", HttpStatusCode.OK, json)
            else -> respond("", HttpStatusCode.NotFound)
        }
    }) { expectSuccess = true }

    /** Due Pikachu numero 67: Caos Nascente (totale 87) e Crepuscolo Mascherato (167). */
    private val catalogApi = object : CatalogApi {
        override suspend fun getExpansions() = listOf(
            Expansion(id = "me04", name = "Caos Nascente", cardCount = 120, officialCount = 99),
            Expansion(id = "sv06", name = "Crepuscolo Mascherato", cardCount = 226, officialCount = 167),
        )
        override suspend fun getExpansionCards(expansionId: String) = cards.filter { it.espansioneId == expansionId }
        override suspend fun getExpansionPrices(expansionId: String) = mapOf("67" to PriceEntry(low = 0.5))
        override suspend fun getFullCatalog() = cards
    }
    private val cards = listOf(
        Card(cardId = "ME04_IT_067.webp", espansioneId = "me04", nome = "Pikachu", tipo = "Lampo", ps = "60", rarity = "Common", stage = "Basic"),
        Card(cardId = "SV06_IT_067.webp", espansioneId = "sv06", nome = "Pikachu", tipo = "Lampo", ps = "70", rarity = "Common", stage = "Basic"),
    )

    private suspend fun viewModel(): ScannerViewModel {
        val store = FileCache(dir)
        val auth = AuthRepository(FirebaseAuthApi(client, "k"), FirestoreApi(client, "p"), store, now = { 0L })
        auth.login("ash@gmail.com", "pikachu")
        commits.clear()
        val collection = CollectionRepository(FirestoreApi(client, "p"), auth, store)
        return ScannerViewModel(CatalogRepository(catalogApi), CollectionWriter(FirestoreWrites(client, "p"), auth, collection))
            .also { vm -> stores += ViewModelStore().apply { put("scanner-${stores.size}", vm) } }
    }

    /** Un fotogramma di Pikachu 067/087, come lo restituisce l'OCR. */
    private val frame = ScannedFrame(
        blocks = listOf(
            OCRTextBlock("Base Pikachu PV 60", 0.9f, ZoneBoundingBox(0.05f, 0.03f, 0.9f, 0.08f), 0.05f),
            OCRTextBlock("Scossa 20", 0.9f, ZoneBoundingBox(0.1f, 0.6f, 0.9f, 0.65f), 0.62f),
        ),
        idStripText = "067/087"
    )

    private suspend fun waitFor(tries: Int = 200, condition: () -> Boolean) = withContext(Dispatchers.Default) {
        repeat(tries) {
            if (condition()) return@withContext
            delay(15)
        }
    }

    @Test
    fun framesBecomeACardAndTheCardGoesInAndComesOut() = runTest {
        val vm = viewModel()

        // Qualche fotogramma uguale: l'aggregatore vuole il numero letto piu' volte.
        repeat(10) {
            if (vm.uiState.pendingCard != null || vm.uiState.candidateCards.isNotEmpty()) return@repeat
            vm.onFrameScanned(frame)
            waitFor(tries = 20) { vm.uiState.pendingCard != null || vm.uiState.candidateCards.isNotEmpty() }
        }
        waitFor { vm.uiState.pendingCard != null || vm.uiState.candidateCards.isNotEmpty() }
        val proposed = vm.uiState.pendingCard ?: vm.uiState.candidateCards.firstOrNull()
        assertNotNull(proposed)
        // Il totale 87 sceglie Caos Nascente fra i due Pikachu 67.
        assertEquals("ita:me04:67", proposed.id)
        assertEquals("67/87", vm.uiState.detectedNumber)

        if (vm.uiState.pendingCard == null) vm.selectCandidate(proposed)
        vm.confirmAdd()
        waitFor { vm.uiState.addedCount == 1 }
        assertEquals(1, vm.uiState.addedCount)
        val created = commits.first { "update" in it }["update"]!!.jsonObject["fields"]!!.jsonObject
        assertEquals("ita:me04:67", created["apiCardId"]!!.jsonObject["stringValue"]!!.jsonPrimitive.content)
        assertEquals("Near Mint", created["condition"]!!.jsonObject["stringValue"]!!.jsonPrimitive.content)
        assertEquals("0.5", created["estimatedValue"]!!.jsonObject["doubleValue"]!!.jsonPrimitive.content)

        // Annulla: la copia appena entrata se ne va (era l'unica, quindi il documento).
        commits.clear()
        vm.undoLastAdd()
        waitFor { vm.uiState.addedCount == 0 }
        assertEquals(0, vm.uiState.addedCount)
        assertTrue(commits.any { "delete" in it })
    }

    @Test
    fun anEmptyFrameRearmsTheScanner() = runTest {
        val vm = viewModel()
        vm.onFrameScanned(frame)
        waitFor { vm.uiState.detectedNumber.isNotEmpty() }
        repeat(2) { vm.onFrameScanned(ScannedFrame()) }
        waitFor { vm.uiState.detectedNumber.isEmpty() }
        assertEquals("", vm.uiState.detectedNumber)
    }
}
