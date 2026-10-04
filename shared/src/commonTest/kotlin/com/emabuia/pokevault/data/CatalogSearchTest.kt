package com.emabuia.pokevault.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogSearchTest {
    private fun card(name: String, number: Int, set: String = "sv03") =
        Card(cardId = "${set.uppercase()}_IT_$number.png", espansioneId = set, nome = name)

    @Test
    fun sameOrderAsAndroid() {
        val cards = listOf(
            card("Charizard-ex", 125),
            card("Charmander", 4),
            card("Charizard", 6),
            card("Pikachu", 25),
            card("Mega Charizard X-ex", 13, "me02"),
        )
        val names = CatalogSearch.search(cards, "charizard").map { it.nome }
        // Uguale (1000), poi inizia con (850), poi parola contenuta (700).
        assertEquals(listOf("Charizard", "Charizard-ex", "Mega Charizard X-ex"), names)
    }

    @Test
    fun accentsCaseAndCamelCaseDoNotMatter() {
        val cards = listOf(card("Pokémon Center", 1), card("PokeGear 3.0", 2), card("Pikachu", 3))
        assertEquals(listOf("Pokémon Center"), CatalogSearch.search(cards, "POKEMON center").map { it.nome })
        assertEquals("poke gear 3 0", CatalogSearch.normalizeNameForLookup("PokeGear 3.0"))
        assertTrue(CatalogSearch.search(cards, "   ").isEmpty())
    }

    @Test
    fun tiesGoByCardNumber() {
        val cards = listOf(card("Pikachu", 160), card("Pikachu", 25), card("Pikachu", 63))
        assertEquals(listOf("25", "63", "160"), CatalogSearch.search(cards, "pikachu").map { it.number })
    }
}
