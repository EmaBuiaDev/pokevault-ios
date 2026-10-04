package com.emabuia.pokevault.screens.card

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.AuthRepository
import com.emabuia.pokevault.data.Card
import com.emabuia.pokevault.data.CatalogRepository
import com.emabuia.pokevault.data.CollectionRepository
import com.emabuia.pokevault.data.CollectionWriter
import com.emabuia.pokevault.data.ExpansionsState
import com.emabuia.pokevault.data.PriceEntry
import com.emabuia.pokevault.data.model.CardOptions
import com.emabuia.pokevault.data.model.PokemonCard
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

sealed interface CardDetailState {
    data object Loading : CardDetailState
    data class Ready(
        val card: Card,
        val price: PriceEntry?,
        val expansionName: String,
        /** Le carte del set in ordine, per scorrere avanti e indietro. */
        val neighbours: List<Card>,
        /** Le stampe che la carta ha davvero (CardOptions.getVariantsForCard, come su Android). */
        val variants: List<String>,
    ) : CardDetailState
    data class Error(val message: String) : CardDetailState
}

/** Quello che serve al modulo "aggiungi alla collezione". */
data class OwnershipState(
    val signedIn: Boolean = false,
    /** Le copie gia' in collezione di questa carta, una per stampa e lingua. */
    val owned: List<PokemonCard> = emptyList(),
    val isSaving: Boolean = false,
    val message: String? = null,
) {
    val isOwned: Boolean get() = owned.isNotEmpty()
    val ownedVariants: Set<String> get() = owned.mapTo(mutableSetOf()) { it.variant }
}

/**
 * Una carta del catalogo: la si cerca fra le carte del suo set, che portano
 * anche i prezzi (e che di solito sono gia' in memoria o sul telefono). Con
 * l'accesso fatto, sa quante copie se ne hanno e le aggiunge o le toglie.
 */
class CardDetailViewModel(
    private val expansionId: String,
    private val cardId: String,
    private val catalog: CatalogRepository,
    private val auth: AuthRepository,
    private val collection: CollectionRepository,
    private val writer: CollectionWriter,
) : ViewModel() {
    private val _state = MutableStateFlow<CardDetailState>(CardDetailState.Loading)
    val state: StateFlow<CardDetailState> = _state.asStateFlow()

    private val _ownership = MutableStateFlow(OwnershipState())
    val ownership: StateFlow<OwnershipState> = _ownership.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = try {
                val set = catalog.expansionCards(expansionId)
                val card = set.cards.firstOrNull { it.cardId == cardId }
                    ?: error("Carta non trovata nel set")
                catalog.ensureExpansions()
                val expansion = (catalog.expansions.value as? ExpansionsState.Ready)
                    ?.expansions?.firstOrNull { it.id == expansionId }
                CardDetailState.Ready(
                    card = card,
                    price = set.priceOf(card),
                    expansionName = expansion?.name ?: expansionId.uppercase(),
                    neighbours = set.cards,
                    // Le carte italiane non hanno prezzi TCGplayer: decide la rarita',
                    // con la data del set per il reverse (nato nel 2002).
                    variants = CardOptions.getVariantsForCard(emptySet(), card.rarity, expansion?.releaseDate),
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                CardDetailState.Error(e.message ?: "Carta non disponibile")
            }
            refreshOwnership()
        }
        // Dopo ogni scrittura (anche da un'altra schermata) le copie si rileggono.
        viewModelScope.launch { collection.changes.drop(1).collect { refreshOwnership() } }
    }

    fun add(variant: String, quantity: Int, condition: String, language: String) {
        val ready = _state.value as? CardDetailState.Ready ?: return
        save(successMessage = "${ready.card.nome} aggiunta!") {
            writer.addFromCatalog(ready.card, ready.expansionName, ready.price, variant, quantity, condition, language)
        }
    }

    /** Il cestino: tutte le stampe di questa carta, come su Android. */
    fun removeAll() {
        val ready = _state.value as? CardDetailState.Ready ?: return
        val apiCardId = ready.card.italianId() ?: return
        save(successMessage = "${ready.card.nome} rimossa") { writer.deleteAllPrints(apiCardId) }
    }

    fun clearMessage() {
        _ownership.value = _ownership.value.copy(message = null)
    }

    private fun save(successMessage: String, block: suspend () -> Unit) {
        if (_ownership.value.isSaving) return
        viewModelScope.launch {
            _ownership.value = _ownership.value.copy(isSaving = true, message = null)
            val message = try {
                block()
                successMessage
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                "Non salvato: ${e.message}"
            }
            _ownership.value = _ownership.value.copy(isSaving = false, message = message)
        }
    }

    private suspend fun refreshOwnership() {
        val session = auth.session.value
        if (session == null) {
            _ownership.value = OwnershipState(signedIn = false)
            return
        }
        val apiCardId = (_state.value as? CardDetailState.Ready)?.card?.italianId()
        val cards = try {
            collection.load()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            collection.cached(session.uid).orEmpty()
        }
        _ownership.value = _ownership.value.copy(
            signedIn = true,
            owned = if (apiCardId == null) emptyList() else cards.filter { it.apiCardId == apiCardId },
        )
    }
}
