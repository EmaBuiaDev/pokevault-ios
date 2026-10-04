package com.emabuia.pokevault.screens.expansions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.Card
import com.emabuia.pokevault.data.CatalogRepository
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchState(
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<Card> = emptyList(),
    val errorMessage: String? = null,
) {
    val isActive: Boolean get() = query.isNotBlank()
}

/** La ricerca del Pokedex: parte poco dopo l'ultima lettera, non a ogni tasto. */
class SearchViewModel(private val catalog: CatalogRepository) : ViewModel() {
    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(query: String) {
        _state.value = _state.value.copy(query = query, errorMessage = null)
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.value = SearchState()
            return
        }
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            _state.value = _state.value.copy(isSearching = true)
            _state.value = try {
                _state.value.copy(isSearching = false, results = catalog.search(query))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _state.value.copy(isSearching = false, errorMessage = "Ricerca non disponibile: ${e.message}")
            }
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 250L
    }
}
