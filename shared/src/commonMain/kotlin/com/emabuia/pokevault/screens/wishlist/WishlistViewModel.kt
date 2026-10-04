package com.emabuia.pokevault.screens.wishlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.WishlistContent
import com.emabuia.pokevault.data.WishlistRepository
import com.emabuia.pokevault.util.AppLocale
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WishlistUiState(
    val isLoading: Boolean = true,
    val lists: List<WishlistContent> = emptyList(),
    val errorMessage: String? = null,
)

class WishlistViewModel(private val repository: WishlistRepository) : ViewModel() {
    private val _state = MutableStateFlow(WishlistUiState())
    val state: StateFlow<WishlistUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = try {
                val lists = repository.wishlists()
                WishlistUiState(isLoading = false, lists = lists.map { async { repository.content(it) } }.awaitAll())
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                WishlistUiState(isLoading = false, errorMessage = "${AppLocale.errorPrefix}: ${e.message}")
            }
        }
    }
}
