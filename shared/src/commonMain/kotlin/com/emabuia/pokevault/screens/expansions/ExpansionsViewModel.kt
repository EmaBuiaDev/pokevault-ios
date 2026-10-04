package com.emabuia.pokevault.screens.expansions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.CatalogRepository
import com.emabuia.pokevault.data.ExpansionsState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ExpansionsViewModel(private val repository: CatalogRepository) : ViewModel() {
    val state: StateFlow<ExpansionsState> = repository.expansions

    init {
        retry()
    }

    fun retry() {
        viewModelScope.launch { repository.refresh() }
    }
}
