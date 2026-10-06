package com.emabuia.pokevault.screens.competitive

import com.emabuia.pokevault.data.model.CardClassifier
import com.emabuia.pokevault.data.model.PokemonCard
import kotlin.test.Test
import kotlin.test.assertEquals

/** L'analisi del deck in modifica: i numeri che finiscono nel documento salvato. */
class DeckAnalyzerTest {

    @Test
    fun onlyPokemonCountForTheTypesAndHpIsAveragedOnCardsThatHaveIt() {
        val cards = listOf(
            PokemonCard(name = "Dreepy", hp = 70, type = "Dragon", subtypes = listOf("Basic")),
            PokemonCard(name = "Dreepy", hp = 70, type = "Dragon", subtypes = listOf("Basic")),
            PokemonCard(name = "Dragapult-ex", hp = 320, type = "Dragon", subtypes = listOf("Stage 2")),
            PokemonCard(name = "Duskull", hp = 60, type = "Psychic, Darkness", subtypes = listOf("Basic")),
            // Le Trainer hanno "Colorless" di ripiego: non devono vincere i tipi.
            PokemonCard(name = "Ultra Ball", supertype = "Trainer", type = "Colorless"),
            PokemonCard(name = "Iono", supertype = "Trainer", type = "Colorless"),
            PokemonCard(name = "Iono", supertype = "Trainer", type = "Colorless"),
            PokemonCard(name = "Iono", supertype = "Trainer", type = "Colorless"),
        )
        val analysis = DeckAnalyzer.analyze(cards) { CardClassifier.classify(it) }

        assertEquals(mapOf("Dragon" to 3, "Psychic" to 1, "Darkness" to 1), analysis.typesCount)
        assertEquals((70 + 70 + 320 + 60) / 4.0, analysis.averageHp)
        assertEquals(mapOf("Pokémon" to 4, "Trainer" to 4), analysis.supertypesCount)
    }

    @Test
    fun anEmptyDeckHasAnEmptyAnalysis() {
        assertEquals(0.0, DeckAnalyzer.analyze(emptyList()) { CardClassifier.classify(it) }.averageHp)
    }
}
