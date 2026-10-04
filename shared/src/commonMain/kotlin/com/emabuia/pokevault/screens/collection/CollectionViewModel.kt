package com.emabuia.pokevault.screens.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.AuthRepository
import com.emabuia.pokevault.data.CollectionRepository
import com.emabuia.pokevault.data.CollectionStats
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.CollectionBrowser
import com.emabuia.pokevault.util.CollectionSort
import com.emabuia.pokevault.util.ExpansionGroupSection
import com.emabuia.pokevault.util.ExpansionOrder
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CollectionUiState(
    val isLoading: Boolean = true,
    val stats: CollectionStats = CollectionStats(),
    /** Le carte divise per espansione: la vista predefinita dell'app Android. */
    val sections: List<ExpansionGroupSection> = emptyList(),
    val errorMessage: String? = null,
)

class CollectionViewModel(
    private val repository: CollectionRepository,
    private val auth: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CollectionUiState())
    val state: StateFlow<CollectionUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Prima la copia sul telefono, cosi' la collezione compare subito.
            auth.session.value?.uid?.let { uid -> repository.cached(uid)?.let { show(it, stillLoading = true) } }
            load()
        }
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        _state.value = _state.value.copy(isLoading = true, errorMessage = null)
        try {
            show(repository.load(), stillLoading = false)
            if (repository.unreadable > 0) {
                _state.value = _state.value.copy(
                    errorMessage = "${repository.unreadable} carte non si sono potute leggere: sull'app Android ci sono ancora.",
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _state.value = _state.value.copy(
                isLoading = false,
                errorMessage = "${AppLocale.errorPrefix}: ${e.message ?: "collezione non disponibile"}",
            )
        }
    }

    private suspend fun show(cards: List<PokemonCard>, stillLoading: Boolean) {
        val computed = withContext(Dispatchers.Default) {
            val groups = CollectionBrowser.sort(
                CollectionBrowser.group(cards, AppLocale.unknownExpansion),
                CollectionSort.NUMBER,
            )
            CollectionRepository.stats(cards) to CollectionBrowser.sections(groups, ExpansionOrder.NAME)
        }
        _state.value = CollectionUiState(
            isLoading = stillLoading,
            stats = computed.first,
            sections = computed.second,
        )
    }
}
