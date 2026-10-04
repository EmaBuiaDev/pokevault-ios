package com.emabuia.pokevault.screens.card

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.Card
import com.emabuia.pokevault.data.CatalogRepository
import com.emabuia.pokevault.data.ExpansionsState
import com.emabuia.pokevault.data.PriceEntry
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CardDetailState {
    data object Loading : CardDetailState
    data class Ready(
        val card: Card,
        val price: PriceEntry?,
        val expansionName: String,
        /** Le carte del set in ordine, per scorrere avanti e indietro. */
        val neighbours: List<Card>,
    ) : CardDetailState
    data class Error(val message: String) : CardDetailState
}

/**
 * Una carta del catalogo: la si cerca fra le carte del suo set, che portano
 * anche i prezzi (e che di solito sono gia' in memoria o sul telefono).
 */
class CardDetailViewModel(
    private val expansionId: String,
    private val cardId: String,
    private val catalog: CatalogRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<CardDetailState>(CardDetailState.Loading)
    val state: StateFlow<CardDetailState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = try {
                val set = catalog.expansionCards(expansionId)
                val card = set.cards.firstOrNull { it.cardId == cardId }
                    ?: error("Carta non trovata nel set")
                catalog.ensureExpansions()
                val name = (catalog.expansions.value as? ExpansionsState.Ready)
                    ?.expansions?.firstOrNull { it.id == expansionId }?.name
                    ?: expansionId.uppercase()
                CardDetailState.Ready(card, set.priceOf(card), name, set.cards)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                CardDetailState.Error(e.message ?: "Carta non disponibile")
            }
        }
    }
}
