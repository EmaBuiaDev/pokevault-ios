package com.emabuia.pokevault.screens.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.WishlistContent
import com.emabuia.pokevault.data.WishlistRepository
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
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

class WishlistViewModel(private val repository: WishlistRepository) : ViewModel() {
    private val _state = MutableStateFlow(WishlistUiState())
    val state: StateFlow<WishlistUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
        // Dopo ogni scrittura (anche dal dettaglio carta) si rilegge.
        viewModelScope.launch { repository.changes.drop(1).collect { load() } }
    }

    private suspend fun load() {
        _state.value = try {
            val lists = repository.wishlists()
            _state.value.copy(isLoading = false, lists = lists.map { viewModelScope.async { repository.content(it) } }.awaitAll())
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _state.value.copy(isLoading = false, errorMessage = "${AppLocale.errorPrefix}: ${e.message}")
        }
    }

    /** Ritorna l'esito a chi chiude la finestra solo dopo il salvataggio. */
    fun create(draft: WishlistDraft, onDone: () -> Unit) = save(onDone) { repository.create(normalizeDraft(draft)) }

    fun update(id: String, draft: WishlistDraft, onDone: () -> Unit) = save(onDone) { repository.update(id, normalizeDraft(draft)) }

    fun delete(id: String) = save(onDone = {}) { repository.delete(id) }

    fun removeCard(listId: String, cardId: String) = save(onDone = {}) { repository.removeCard(listId, cardId) }

    private fun save(onDone: () -> Unit, block: suspend () -> Unit) {
        if (_state.value.isSaving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, errorMessage = null)
            try {
                block()
                _state.value = _state.value.copy(isSaving = false)
                onDone()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.value = _state.value.copy(isSaving = false, errorMessage = "Non salvato: ${e.message}")
            }
        }
    }

    companion object {
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
