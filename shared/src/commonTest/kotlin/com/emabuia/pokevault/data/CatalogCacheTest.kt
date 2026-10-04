package com.emabuia.pokevault.data

import kotlinx.coroutines.test.runTest
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.serialization.builtins.ListSerializer
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class CatalogCacheTest {
    private val dir = Path(SystemTemporaryDirectory, "pokevault-test-${Random.nextLong().toULong()}").toString()
    private var clock = 1_000_000L

    private fun cache() = FileCache(dir, now = { clock })

    @AfterTest
    fun cleanUp() {
        if (!SystemFileSystem.exists(Path(dir))) return
        SystemFileSystem.list(Path(dir)).forEach { SystemFileSystem.delete(it) }
        SystemFileSystem.delete(Path(dir))
    }

    /** Un'API finta: [online] decide se risponde, e conta le chiamate. */
    private class FakeApi : CatalogApi {
        var online = true
        var cardCalls = 0
        var expansions = listOf(Expansion(id = "me05", name = "Buio Pesto", releaseDate = "2026-07-17"))

        private fun check() { if (!online) throw IllegalStateException("offline") }

        override suspend fun getExpansions(): List<Expansion> { check(); return expansions }
        override suspend fun getExpansionCards(expansionId: String): List<Card> {
            check(); cardCalls++
            return listOf(Card(cardId = "ME05_IT_002.webp", nome = "Grubbin"), Card(cardId = "ME05_IT_001.webp", nome = "Tropius"))
        }
        override suspend fun getExpansionPrices(expansionId: String) = mapOf("1" to PriceEntry(low = 0.02))
    }

    @Test
    fun roundTripAndUnreadableFile() {
        val cache = cache()
        val serializer = ListSerializer(Expansion.serializer())
        assertNull(cache.read("expansions", serializer))

        cache.write("expansions", serializer, listOf(Expansion(id = "me05", name = "Buio Pesto")))
        val read = cache.read("expansions", serializer)
        assertEquals("Buio Pesto", read?.data?.single()?.name)
        assertEquals(1_000_000L, read?.savedAt)

        // Un file rovinato e' una cache vuota, non un crash.
        SystemFileSystem.sink(Path(dir, "expansions.json")).use { }
        assertNull(cache.read("expansions", serializer))
    }

    @Test
    fun offlineStartShowsWhatWasSavedBefore() = runTest {
        val api = FakeApi()
        CatalogRepository(api, cache()).refresh()

        // Riavvio senza rete: stesso disco, repository nuovo.
        api.online = false
        val restarted = CatalogRepository(api, cache())
        restarted.ensureExpansions()
        val state = assertIs<ExpansionsState.Ready>(restarted.expansions.value)
        assertEquals("Buio Pesto", state.expansions.single().name)
    }

    @Test
    fun offlineWithNothingSavedIsAnError() = runTest {
        val api = FakeApi().apply { online = false }
        val repository = CatalogRepository(api, cache())
        repository.ensureExpansions()
        assertIs<ExpansionsState.Error>(repository.expansions.value)
    }

    @Test
    fun cardsComeFromDiskWhileFreshAndFromTheNetworkAfter() = runTest {
        val api = FakeApi()
        CatalogRepository(api, cache()).expansionCards("me05")
        assertEquals(1, api.cardCalls)

        // Dopo un'ora: dal disco, nessuna chiamata, e in ordine di numero.
        clock += 60L * 60 * 1000
        val fromDisk = CatalogRepository(api, cache()).expansionCards("me05")
        assertEquals(1, api.cardCalls)
        assertEquals(listOf("1", "2"), fromDisk.cards.map { it.number })
        assertEquals("€ 0,02", fromDisk.priceOf(fromDisk.cards.first())?.displayText())

        // Dopo due giorni: si riscarica.
        clock += 48L * 60 * 60 * 1000
        CatalogRepository(api, cache()).expansionCards("me05")
        assertEquals(2, api.cardCalls)

        // Altri due giorni, offline: le carte vecchie valgono piu' di un errore.
        clock += 48L * 60 * 60 * 1000
        api.online = false
        val stale = CatalogRepository(api, cache()).expansionCards("me05")
        assertEquals(2, stale.cards.size)
    }
}
