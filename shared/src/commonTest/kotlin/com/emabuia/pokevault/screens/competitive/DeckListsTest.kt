package com.emabuia.pokevault.screens.competitive

import com.emabuia.pokevault.data.model.CardClassifier
import com.emabuia.pokevault.data.model.Deck
import com.emabuia.pokevault.data.model.PokemonCard
import kotlin.test.Test
import kotlin.test.assertEquals

/** La decklist esportata: lo stesso testo che produce buildPtcgDecklist su Android. */
class DeckListsTest {

    private val cards = listOf(
        PokemonCard(id = "a1", name = "Dreepy", set = "TWM", cardNumber = "128", hp = 70, subtypes = listOf("Basic"), apiCardId = "ita:sv06:128"),
        PokemonCard(id = "a2", name = "Dreepy", set = "TWM", cardNumber = "128", hp = 70, subtypes = listOf("Basic"), apiCardId = "ita:sv06:128"),
        PokemonCard(id = "b1", name = "Ultra Ball", set = "SVI", cardNumber = "196", supertype = "Trainer", subtypes = listOf("Item"), apiCardId = "ita:sv01:196"),
        // Solo-deck: nella decklist c'e' lo stesso, e' la lista che si porta al tavolo.
        PokemonCard(id = "c1", name = "Energia base Psico", supertype = "Energy", deckOnly = true),
        PokemonCard(id = "c2", name = "Energia base Psico", supertype = "Energy", deckOnly = true),
    ).associateBy { it.id }

    private fun key(card: PokemonCard) =
        card.apiCardId.ifEmpty { "${card.name}-${card.set}-${card.cardNumber}-${card.variant}" }

    @Test
    fun decklistGroupsCopiesBySectionWithSetCodes() {
        val deck = Deck(name = "Dragapult", cards = listOf("a1", "b1", "a2", "c1", "c2", "sparita"))
        val text = DeckLists.ptcgDecklist(deck, cards, ::key) { CardClassifier.classify(it) }
        assertEquals(
            """
            Dragapult

            Pokémon: 2
            2 Dreepy TWM 128

            Trainer: 1
            1 Ultra Ball SVI 196

            Energy: 2
            2 Energia base Psico
            """.trimIndent(),
            text,
        )
    }

    @Test
    fun aDeckWithoutNameIsCalledDeck() {
        val text = DeckLists.ptcgDecklist(Deck(cards = listOf("b1")), cards, ::key) { CardClassifier.classify(it) }
        assertEquals("Deck", text.lines().first())
    }
}
