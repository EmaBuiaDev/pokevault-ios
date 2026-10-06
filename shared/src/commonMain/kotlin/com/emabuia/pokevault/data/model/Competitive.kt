package com.emabuia.pokevault.data.model

import com.emabuia.pokevault.util.PokemonSpriteResolver
import kotlinx.serialization.Serializable

/**
 * Un torneo del Match log: data/model/Tournament.kt di Android, stessi campi
 * su users/{uid}/tournaments.
 */
@Serializable
data class Tournament(
    val id: String = "",
    val location: String = "",
    val date: Timestamp? = null,
    val participants: Int = 0,
    val registrationFee: Double = 0.0,
    val type: String = "",              // Challenge, Cup, Local
    val format: String = "",            // Standard, Expanded, GLC, ecc.
    val deckName: String = "",          // Nome mazzo scelto
    val deckId: String = "",            // Riferimento al deck dell'utente (opzionale)
    val createdAt: Timestamp? = null
) {
    companion object {
        val TYPES = listOf("Challenge", "Cup", "Local")
        val FORMATS = listOf("Standard", "Expanded", "GLC", "Unlimited", "Theme", "Other")
    }
}

/** Una partita: data/model/MatchLog.kt di Android, su users/{uid}/match_logs. */
@Serializable
data class MatchLog(
    val id: String = "",
    val tournamentId: String = "",      // Riferimento al torneo
    val round: Int = 0,                 // Numero turno
    val result: String = "",            // W, L, T (Win, Loss, Tie)
    val opponentName: String = "",      // Nome avversario
    val opponentDeck: String = "",      // Mazzo avversario
    val notes: String = "",             // Note, tech, matchup, sensazioni
    val createdAt: Timestamp? = null
) {
    companion object {
        val RESULTS = listOf("W", "L", "T")
    }
}

/**
 * Un mazzo: data/model/Deck.kt di Android, su users/{uid}/decks. [cards] sono
 * gli id dei documenti della collezione, una voce per copia.
 */
@Serializable
data class Deck(
    val id: String = "",
    val name: String = "",
    val cards: List<String> = emptyList(),
    val createdAt: Timestamp? = null,
    val mainTypes: List<String> = emptyList(),
    val averageHp: Double = 0.0,
    val totalCards: Int = 0,
    val recommendedEnergy: List<String> = emptyList(),
    val coverImageUrl: String = "",
    val coverImageUrls: List<String> = emptyList(),
    /** Il deck contiene almeno una carta che l'utente non possiede (vedi Android). */
    val deckOnly: Boolean = false
) {
    fun displayCoverImageUrls(): List<String> {
        return (coverImageUrls + coverImageUrl)
            .filter { it.isNotBlank() }
            .distinct()
            .take(2)
    }

    /**
     * Le copertine scelte a mano, se sono sprite: quelle vecchie con le
     * immagini delle carte si scartano, come su Android.
     */
    fun chosenSpriteCovers(): List<String> =
        displayCoverImageUrls().filter { PokemonSpriteResolver.isSpriteUrl(it) }
}

/** L'analisi di un mazzo, calcolata e mai salvata: DeckAnalysis di Android. */
data class DeckAnalysis(
    val typesCount: Map<String, Int> = emptyMap(),
    val commonWeaknesses: List<String> = emptyList(),
    val averageHp: Double = 0.0,
    val recommendedEnergy: List<String> = emptyList(),
    val synergies: List<String> = emptyList(),
    val supertypesCount: Map<String, Int> = emptyMap()
)
