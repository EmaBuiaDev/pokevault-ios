package com.emabuia.pokevault.screens.cards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.CatalogRepository
import com.emabuia.pokevault.data.ExpansionCards
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CardsState {
    data object Loading : CardsState
    data class Ready(val content: ExpansionCards) : CardsState
    data class Error(val message: String) : CardsState
}

class ExpansionCardsViewModel(
    private val expansionId: String,
    private val repository: CatalogRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<CardsState>(CardsState.Loading)
    val state: StateFlow<CardsState> = _state.asStateFlow()

    init {
        retry()
    }

    fun retry() {
        viewModelScope.launch {
            _state.value = CardsState.Loading
            _state.value = try {
                CardsState.Ready(repository.expansionCards(expansionId))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                CardsState.Error(e.message ?: e::class.simpleName.orEmpty())
            }
        }
    }
}
