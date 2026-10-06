package com.emabuia.pokevault.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Le carte che lo Scanner propone per una lettura: numero, totale del set, nome. */
class ScannerCatalogSearchTest {

    private val expansions = listOf(
        Expansion(id = "me04", name = "Caos Nascente", cardCount = 120, officialCount = 99),
        Expansion(id = "sv06", name = "Crepuscolo Mascherato", cardCount = 226, officialCount = 167),
        Expansion(id = "sv08", name = "Scintille Folgoranti", cardCount = 252, officialCount = 191),
    )

    private val catalog = listOf(
        Card(cardId = "ME04_IT_067.webp", espansioneId = "me04", nome = "Pikachu"),
        Card(cardId = "SV06_IT_067.webp", espansioneId = "sv06", nome = "Pikachu"),
        Card(cardId = "SV08_IT_067.webp", espansioneId = "sv08", nome = "Raichu"),
        Card(cardId = "SV06_IT_068.webp", espansioneId = "sv06", nome = "Pikachu"),
    )

    @Test
    fun theNumberFiltersAndThePrintedTotalPicksTheExpansion() {
        // "067/087": me04 ha il totale corretto a mano (87), come su Android.
        val hits = ScannerCatalogSearch.search(catalog, expansions, name = "Pikachu", number = "067", setTotal = "087", targetSetId = null)
        assertEquals("me04", hits.first().card.espansioneId)
        assertEquals(87, hits.first().printedTotal)
        // Raichu ha lo stesso numero ma un altro nome: il filtro sul nome lo toglie.
        assertTrue(hits.none { it.card.nome == "Raichu" })
        assertTrue(hits.none { it.card.number == "68" })
    }

    @Test
    fun theOfficialCountIsThePrintedTotal() {
        val hits = ScannerCatalogSearch.search(catalog, expansions, name = "Pikachu", number = "67", setTotal = "167", targetSetId = null)
        assertEquals("sv06", hits.first().card.espansioneId)
        assertEquals(167, hits.first().printedTotal)
    }

    @Test
    fun aDirtyNameStillGivesTheCandidatesForTheNumber() {
        // L'OCR ha letto spazzatura al posto del nome: si ripiega sul solo numero.
        val hits = ScannerCatalogSearch.search(catalog, expansions, name = "Xqzv", number = "67", setTotal = null, targetSetId = null)
        assertEquals(3, hits.size)
    }

    @Test
    fun aSlightlyWrongNameStillMatches() {
        assertTrue(ScannerCatalogSearch.scannerNameScore("pikachu", "pikachv") >= 36)
        assertEquals(0, ScannerCatalogSearch.scannerNameScore("pikachu", "charizard"))
        assertEquals("mr mime", ScannerCatalogSearch.normalizeNameForLookup("Mr. Mimé"))
    }

    @Test
    fun nothingReadNothingProposed() {
        assertTrue(ScannerCatalogSearch.search(catalog, expansions, name = null, number = null, setTotal = null, targetSetId = null).isEmpty())
    }
}
