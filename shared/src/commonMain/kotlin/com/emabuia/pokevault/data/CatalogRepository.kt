package com.emabuia.pokevault.data

import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface ExpansionsState {
    data object Loading : ExpansionsState
    data class Ready(val expansions: List<Expansion>) : ExpansionsState
    data class Error(val message: String) : ExpansionsState
}

class CatalogRepository(private val api: CatalogApi) {
    private val _expansions = MutableStateFlow<ExpansionsState>(ExpansionsState.Loading)
    val expansions: StateFlow<ExpansionsState> = _expansions.asStateFlow()

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
}
