package com.emabuia.pokevault.data

import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface ExpansionsState {
    data object Loading : ExpansionsState
    data class Ready(val expansions: List<Expansion>) : ExpansionsState
    data class Error(val message: String) : ExpansionsState
}

/** Le carte di un'espansione, in ordine di numero, con i prezzi per numero. */
data class ExpansionCards(
    val cards: List<Card>,
    val prices: Map<String, PriceEntry>,
) {
    fun priceOf(card: Card): PriceEntry? = card.number?.let { prices[it] }
}

class CatalogRepository(private val api: CatalogApi) {
    private val _expansions = MutableStateFlow<ExpansionsState>(ExpansionsState.Loading)
    val expansions: StateFlow<ExpansionsState> = _expansions.asStateFlow()

    private val expansionsMutex = Mutex()

    // Solo in memoria, per la sessione: come getExpansionCards() su Android.
    private val cardsCache = mutableMapOf<String, ExpansionCards>()

    /** Home e Pokedex chiedono lo stesso elenco: lo scarica solo il primo. */
    suspend fun ensureExpansions() = expansionsMutex.withLock {
        if (_expansions.value !is ExpansionsState.Ready) refresh()
    }

    suspend fun refresh() {
        _expansions.value = ExpansionsState.Loading
        _expansions.value = try {
            // Dalla piu' recente, come l'elenco dell'app Android.
            ExpansionsState.Ready(api.getExpansions().sortedByDescending { it.releaseDate.orEmpty() })
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            ExpansionsState.Error(e.message ?: e::class.simpleName.orEmpty())
        }
    }

    suspend fun expansionCards(expansionId: String): ExpansionCards {
        cardsCache[expansionId]?.let { return it }
        val loaded = coroutineScope {
            val cards = async { api.getExpansionCards(expansionId) }
            // Senza prezzi le carte si mostrano lo stesso: non e' un errore della schermata.
            val prices = async {
                try {
                    api.getExpansionPrices(expansionId)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    emptyMap()
                }
            }
            ExpansionCards(cards.await().sortedWith(Card.byNumber), prices.await())
        }
        cardsCache[expansionId] = loaded
        return loaded
    }
}
