package com.emabuia.pokevault.screens.stats

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.CatalogRepository
import com.emabuia.pokevault.data.CollectionRepository
import com.emabuia.pokevault.data.CollectionStats
import com.emabuia.pokevault.data.Expansion
import com.emabuia.pokevault.data.ExpansionsState
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.util.AppLocale
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SetCompletion(
    val setName: String,
    val ownedUnique: Int,
    val totalCards: Int
) {
    /**
     * Limitata a 0..1: e' usata direttamente come frazione in
     * Modifier.fillMaxWidth(), che richiede quell'intervallo. Con le secret rare
     * ownedUnique puo' superare totalCards (il totale stampato del set), e senza
     * questo vincolo la barra di completamento faceva crashare lo schermo.
     */
    val percentage: Float
        get() = if (totalCards > 0) (ownedUnique.toFloat() / totalCards).coerceIn(0f, 1f) else 0f
}

data class StatsUiState(
    val stats: CollectionStats = CollectionStats(),
    val cards: List<PokemonCard> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val cardsBySet: List<Pair<String, Int>> = emptyList(),
    val setCompletions: List<SetCompletion> = emptyList(),
    val cardsByRarity: List<Pair<String, Int>> = emptyList(),
    val cardsByType: List<Pair<String, Int>> = emptyList(),
    val gradedCount: Int = 0,
    val averageValue: Double = 0.0
)

/**
 * StatsViewModel dell'app Android, sulla collezione letta da
 * CollectionRepository e coi totali dei set dall'elenco espansioni.
 *
 * Una differenza voluta: "Carte Uniche" conta le carte (collectionCardKey),
 * come la Collezione. Su Android Statistiche usa collectionGroupKey, che conta
 * le stampe, e le due schermate dicono numeri diversi sotto la stessa
 * etichetta (Normale + Reverse della stessa carta: 1 in Collezione, 2 qui).
 */
class StatsViewModel(
    private val collection: CollectionRepository,
    private val catalog: CatalogRepository,
) : ViewModel() {

    var uiState by mutableStateOf(StatsUiState())
        private set

    init {
        viewModelScope.launch {
            try {
                val cards = collection.load()
                catalog.ensureExpansions()
                val expansions = (catalog.expansions.value as? ExpansionsState.Ready)?.expansions.orEmpty()
                uiState = withContext(Dispatchers.Default) { buildStats(cards, expansions) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                uiState = uiState.copy(isLoading = false, errorMessage = e.message ?: AppLocale.unknownError)
            }
        }
    }

    private fun buildStats(cards: List<PokemonCard>, expansions: List<Expansion>): StatsUiState {
        val totalCards = cards.sumOf { it.quantity }
        val totalValue = cards.sumOf { it.estimatedValue * it.quantity }

        val stats = CollectionRepository.stats(cards).copy(
            mostValuable = cards.maxByOrNull { it.estimatedValue }?.name ?: "-"
        )

        val bySet = cards.groupBy { AppLocale.displaySetName(it.set).ifBlank { AppLocale.unknown } }
            .mapValues { (_, v) -> v.sumOf { it.quantity } }
            .entries.sortedByDescending { it.value }
            .map { it.key to it.value }

        // Il totale del set: quello stampato sulle carte se c'e' (come
        // linkedBase.printedTotal su Android), altrimenti quante carte ha.
        val totalsByName = HashMap<String, Int>(expansions.size * 2)
        // getOrPut e non putIfAbsent (solo JVM): vince la prima espansione con quel nome, come su Android.
        expansions.forEach { totalsByName.getOrPut(it.name) { it.officialCount ?: it.cardCount } }

        val completions = cards.asSequence()
            .filter { it.set.isNotBlank() }
            .groupBy { it.set }
            .map { (rawSetName, setCards) ->
                val displayName = AppLocale.displaySetName(rawSetName)
                val uniqueOwned = setCards.asSequence()
                    .map { it.apiCardId }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .count()
                SetCompletion(displayName, uniqueOwned, totalsByName[rawSetName] ?: totalsByName[displayName] ?: 0)
            }
            .filter { it.totalCards > 0 }
            .sortedByDescending { it.percentage }

        val byRarity = cards.groupBy {
                AppLocale.translateRarity(it.rarity).ifBlank { AppLocale.unknown }
            }
            .mapValues { (_, v) -> v.sumOf { it.quantity } }
            .entries.sortedByDescending { it.value }
            .map { it.key to it.value }

        val byType = cards.groupBy {
                // Le carte a doppio tipo salvate prima del fix hanno type =
                // "Tipo1, Tipo2": si raggruppano sul primo tipo.
                val primaryType = it.type.substringBefore(",").trim()
                AppLocale.translateType(primaryType).ifBlank { AppLocale.other }
            }
            .mapValues { (_, v) -> v.sumOf { it.quantity } }
            .entries.sortedByDescending { it.value }
            .map { it.key to it.value }

        return StatsUiState(
            stats = stats,
            cards = cards,
            isLoading = false,
            cardsBySet = bySet,
            setCompletions = completions,
            cardsByRarity = byRarity,
            cardsByType = byType,
            gradedCount = cards.count { it.isGraded },
            averageValue = if (totalCards > 0) totalValue / totalCards else 0.0
        )
    }
}
