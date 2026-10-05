package com.emabuia.pokevault.screens.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.PremiumRepository
import com.emabuia.pokevault.data.WishlistContent
import com.emabuia.pokevault.data.WishlistRepository
import com.emabuia.pokevault.data.model.Wishlist
import com.emabuia.pokevault.data.model.WishlistAccents
import com.emabuia.pokevault.data.model.WishlistDraft
import com.emabuia.pokevault.data.model.WishlistIcons
import com.emabuia.pokevault.util.AppLocale
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

data class WishlistUiState(
    val isLoading: Boolean = true,
    val lists: List<WishlistContent> = emptyList(),
    /** Le liste appena lette, prima di carte e prezzi: al picker bastano queste. */
    val wishlists: List<Wishlist> = emptyList(),
    val isSaving: Boolean = false,
    /** Le liste non si leggono: al posto dell'elenco. */
    val errorMessage: String? = null,
    /** Una scrittura non e' andata: sopra l'elenco, che resta. */
    val saveError: String? = null,
    /** null finche' il server non ha risposto. */
    val isPremium: Boolean? = null,
) {
    /**
     * canCreateWishlist su Android: gratis se ne tiene una. Se il server non
     * risponde non si blocca nessuno, come PremiumManager quando non sa.
     */
    val canCreate: Boolean get() = isPremium != false || wishlists.size < PremiumRepository.FREE_WISHLIST_LIMIT

    /** getWishlistIdsForCard: le liste in cui la carta c'e' gia'. */
    fun listIdsWith(cardId: String): Set<String> =
        wishlists.filter { cardId in it.cardIds }.mapTo(mutableSetOf()) { it.id }
}

class WishlistViewModel(
    private val repository: WishlistRepository,
    private val premium: PremiumRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(WishlistUiState())
    val state: StateFlow<WishlistUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch { _state.value = _state.value.copy(isPremium = premium.isPremium()) }
        // Dopo ogni scrittura (anche dal dettaglio carta) si rilegge.
        viewModelScope.launch { repository.changes.drop(1).collect { load() } }
    }

    private suspend fun load() {
        _state.value = try {
            val lists = repository.wishlists()
            _state.value = _state.value.copy(wishlists = lists)
            _state.value.copy(isLoading = false, errorMessage = null, lists = lists.map { viewModelScope.async { repository.content(it) } }.awaitAll())
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _state.value.copy(isLoading = false, errorMessage = "${AppLocale.errorPrefix}: ${e.message}")
        }
    }

    /** Ritorna l'esito a chi chiude la finestra solo dopo il salvataggio. */
    fun create(draft: WishlistDraft, onDone: () -> Unit) = save(onDone) {
        check(_state.value.canCreate) { "con l'account gratuito si tiene una sola wishlist" }
        repository.create(validDraft(draft))
    }

    /** createWishlistAndAddCard: dal dettaglio, la lista nasce gia' con la carta dentro. */
    fun createWith(draft: WishlistDraft, cardId: String, onDone: () -> Unit) = save(onDone) {
        check(_state.value.canCreate) { "con l'account gratuito si tiene una sola wishlist" }
        repository.create(validDraft(draft), firstCardIds = listOf(cardId))
    }

    /** addCardsToWishlists di Android: lo stesso blocco di carte in piu' liste, col solo esito. */
    fun addCardsToWishlists(listIds: Set<String>, cardIds: List<String>, onResult: (Boolean) -> Unit) {
        if (listIds.isEmpty() || cardIds.isEmpty()) return onResult(false)
        viewModelScope.launch {
            val ok = try {
                repository.addCards(listIds, cardIds)
                true
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                false
            }
            onResult(ok)
        }
    }

    /** updateCardWishlists: quello che e' spuntato nel picker diventa la verita'. */
    fun setCardLists(cardId: String, target: Set<String>, onDone: () -> Unit) {
        val current = _state.value.listIdsWith(cardId)
        if (target == current) return onDone()
        save(onDone) { repository.moveCard(cardId, addTo = target - current, removeFrom = current - target) }
    }

    fun update(id: String, draft: WishlistDraft, onDone: () -> Unit) = save(onDone) { repository.update(id, validDraft(draft)) }

    private fun validDraft(draft: WishlistDraft): WishlistDraft = normalizeDraft(draft).also {
        require(isValidName(it.name)) { "il nome va da 1 a 40 caratteri" }
    }

    fun clearError() {
        _state.value = _state.value.copy(saveError = null)
    }

    fun delete(id: String) = save(onDone = {}) { repository.delete(id) }

    fun removeCard(listId: String, cardId: String) = save(onDone = {}) { repository.removeCard(listId, cardId) }

    private fun save(onDone: () -> Unit, block: suspend () -> Unit) {
        if (_state.value.isSaving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, saveError = null)
            try {
                block()
                _state.value = _state.value.copy(isSaving = false)
                onDone()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.value = _state.value.copy(isSaving = false, saveError = "Non salvato: ${e.message}")
            }
        }
    }

    companion object {
        /** isValidWishlistName su Android. */
        fun isValidName(name: String): Boolean = name.trim().let { it.isNotEmpty() && it.length <= 40 }

        /** Come su Android: "30", "30,50" e "30 €" sono tutti trenta euro. */
        fun parseBudget(raw: String): Double {
            val normalized = raw.trim().replace(',', '.').replace("€", "").trim()
            if (normalized.isEmpty()) return 0.0
            return normalized.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
        }

        /**
         * L'accento si risolve sulla chiave originale: una lista creata col vecchio
         * catalogo di icone, modificata senza toccare il colore, tiene il suo.
         */
        fun normalizeDraft(draft: WishlistDraft): WishlistDraft = draft.copy(
            name = draft.name.trim(),
            iconKey = WishlistIcons.normalize(draft.iconKey),
            accentKey = WishlistAccents.normalize(draft.accentKey, draft.iconKey),
            budgetEur = draft.budgetEur.coerceAtLeast(0.0),
        )
    }
}
