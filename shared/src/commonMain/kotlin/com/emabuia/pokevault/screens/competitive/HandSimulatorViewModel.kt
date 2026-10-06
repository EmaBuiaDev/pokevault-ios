package com.emabuia.pokevault.screens.competitive

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.CollectionRepository
import com.emabuia.pokevault.data.CompetitiveRepository
import com.emabuia.pokevault.data.PremiumRepository
import com.emabuia.pokevault.data.model.Deck
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.simulator.HandSimulatorLocalStore
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Quello che l'Hand Simulator prendeva da DeckLabViewModel su Android: i
 * mazzi e le carte, solo-deck comprese (un deck di prova si simula per le
 * sue 60 carte). Piu' il limite gratuito di PremiumManager.
 *
 * Su Android la schermata ripara anche lo stadio delle carte importate senza
 * (ensureCardStagesFromCatalog, che scrive su Firestore): qui no. Quelle
 * carte le segnala comunque deckAccuracyWarnings, come su Android prima della
 * riparazione.
 */
class HandSimulatorViewModel(
    private val competitive: CompetitiveRepository,
    private val collection: CollectionRepository,
    private val premium: PremiumRepository,
    val localStore: HandSimulatorLocalStore,
) : ViewModel() {

    var decks by mutableStateOf<List<Deck>>(emptyList())
        private set

    var allCards by mutableStateOf<List<PokemonCard>>(emptyList())
        private set

    /** null finche' il Worker non ha risposto: nel dubbio non si blocca niente. */
    var isPremium by mutableStateOf<Boolean?>(null)
        private set

    init {
        reload()
        viewModelScope.launch { isPremium = premium.isPremium() }
        viewModelScope.launch { competitive.changes.drop(1).collect { reload() } }
        viewModelScope.launch { collection.changes.drop(1).collect { reload() } }
    }

    private fun reload() {
        viewModelScope.launch { decks = load { competitive.decks() } ?: decks }
        viewModelScope.launch { allCards = load { collection.loadIncludingDeckOnly() } ?: allCards }
    }

    private suspend fun <T> load(block: suspend () -> T): T? = try {
        block()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        null
    }

    /**
     * canRunHandSimulator di PremiumManager: senza Premium un'analisi sola,
     * e solo se si ha un mazzo solo (il limite gratuito dei mazzi).
     */
    fun canRunHandSimulator(deckId: String): Boolean {
        if (isPremium != false) return true
        if (deckId.isBlank()) return false
        if (decks.size != PremiumRepository.FREE_DECK_LIMIT) return false
        return localStore.runsFor(deckId) < 1
    }

    fun consumeHandSimulatorRun(deckId: String) {
        if (isPremium == false) localStore.consumeRun(deckId)
    }

    fun getHandSimulatorRuns(deckId: String): Int = localStore.runsFor(deckId)
}
