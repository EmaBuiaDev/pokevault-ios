package com.emabuia.pokevault.screens.collection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.AuthRepository
import com.emabuia.pokevault.data.CollectionRepository
import com.emabuia.pokevault.data.CollectionStats
import com.emabuia.pokevault.data.CollectionWriter
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.CardCategory
import com.emabuia.pokevault.util.CardGroup
import com.emabuia.pokevault.util.CollectionBrowser
import com.emabuia.pokevault.util.CollectionFacets
import com.emabuia.pokevault.util.CollectionFilter
import com.emabuia.pokevault.util.CollectionLayout
import com.emabuia.pokevault.util.CollectionSort
import com.emabuia.pokevault.util.ExpansionGroupSection
import com.emabuia.pokevault.util.ExpansionOrder
import com.emabuia.pokevault.util.ValueBucket
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** CollectionUiState dell'app Android, campo per campo. */
data class CollectionUiState(
    val cards: List<PokemonCard> = emptyList(),
    /** Tutte le carte, una per tessera. */
    val groups: List<CardGroup> = emptyList(),
    /** Quelle che passano i filtri, gia' ordinate. */
    val visibleGroups: List<CardGroup> = emptyList(),
    /** Le stesse, divise per espansione. */
    val sections: List<ExpansionGroupSection> = emptyList(),
    val facets: CollectionFacets = CollectionFacets(),
    val stats: CollectionStats = CollectionStats(),
    val isLoading: Boolean = true,
    val isGridView: Boolean = true,
    val gridColumns: Int = 3,
    val layout: CollectionLayout = CollectionLayout.BY_EXPANSION,
    val filter: CollectionFilter = CollectionFilter(),
    val sort: CollectionSort = CollectionSort.NUMBER,
    val expansionOrder: ExpansionOrder = ExpansionOrder.NAME,
    val errorMessage: String? = null,
    val successMessage: String? = null,
) {
    val searchQuery: String get() = filter.query
    val visibleQuantity: Int get() = visibleGroups.sumOf { it.totalQuantity }
    val visibleValue: Double get() = visibleGroups.sumOf { it.totalValue }
}

/**
 * CollectionViewModel dell'app Android: filtri, ricerca, ordinamenti e viste,
 * con le carte da [CollectionRepository] e le scritture da [CollectionWriter].
 *
 * Due cose di Android restano fuori: le correzioni automatiche su Firestore
 * mentre si legge (prezzi mancanti, codici espansione vecchi) e la
 * cancellazione di piu' carte selezionate insieme.
 */
class CollectionViewModel(
    private val repository: CollectionRepository,
    private val auth: AuthRepository,
    private val writer: CollectionWriter,
) : ViewModel() {

    var uiState by mutableStateOf(CollectionUiState())
        private set

    private var recomputeJob: Job? = null

    init {
        viewModelScope.launch {
            // Prima la copia sul telefono, cosi' la collezione compare subito.
            auth.session.value?.uid?.let { uid -> repository.cached(uid)?.let { applyCardsSnapshot(it, stillLoading = true) } }
            load()
        }
        // Dopo ogni scrittura, da qui o dal dettaglio carta, si rilegge.
        viewModelScope.launch { repository.changes.drop(1).collect { load() } }
    }

    fun refresh() {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        uiState = uiState.copy(errorMessage = null)
        try {
            applyCardsSnapshot(repository.load(), stillLoading = false)
            if (repository.unreadable > 0) {
                uiState = uiState.copy(
                    errorMessage = "${repository.unreadable} carte non si sono potute leggere: sull'app Android ci sono ancora.",
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            uiState = uiState.copy(
                isLoading = false,
                errorMessage = "${AppLocale.errorPrefix}: ${e.message ?: "collezione non disponibile"}",
            )
        }
    }

    /**
     * Un nuovo elenco di carte: si ricostruiscono le tessere e si riapplicano
     * i filtri, tutto fuori dal thread principale. Se intanto i criteri sono
     * cambiati, si rifa' coi criteri nuovi (come su Android).
     */
    private suspend fun applyCardsSnapshot(cards: List<PokemonCard>, stillLoading: Boolean) {
        val criteria = uiState
        val computed = withContext(Dispatchers.Default) {
            val groups = CollectionBrowser.group(cards, AppLocale.unknownExpansion)
            val visible = CollectionBrowser.sort(CollectionBrowser.filter(groups, criteria.filter), criteria.sort)
            Computed(
                stats = CollectionRepository.stats(cards),
                groups = groups,
                facets = CollectionBrowser.facets(groups),
                visible = visible,
                sections = CollectionBrowser.sections(visible, criteria.expansionOrder),
            )
        }
        val criteriaChanged = uiState.filter != criteria.filter ||
            uiState.sort != criteria.sort ||
            uiState.expansionOrder != criteria.expansionOrder
        uiState = uiState.copy(
            cards = cards,
            groups = computed.groups,
            facets = computed.facets,
            stats = computed.stats,
            visibleGroups = if (criteriaChanged) uiState.visibleGroups else computed.visible,
            sections = if (criteriaChanged) uiState.sections else computed.sections,
            isLoading = stillLoading,
        )
        if (criteriaChanged) scheduleRecompute()
    }

    private class Computed(
        val stats: CollectionStats,
        val groups: List<CardGroup>,
        val facets: CollectionFacets,
        val visible: List<CardGroup>,
        val sections: List<ExpansionGroupSection>,
    )

    /** Riapplica filtri e ordinamenti; una modifica nuova annulla il calcolo in corso. */
    private fun scheduleRecompute(debounceMs: Long = 0L) {
        recomputeJob?.cancel()
        recomputeJob = viewModelScope.launch {
            if (debounceMs > 0L) delay(debounceMs)
            val snapshot = uiState
            val (visible, sections) = withContext(Dispatchers.Default) {
                val v = CollectionBrowser.sort(CollectionBrowser.filter(snapshot.groups, snapshot.filter), snapshot.sort)
                v to CollectionBrowser.sections(v, snapshot.expansionOrder)
            }
            uiState = uiState.copy(visibleGroups = visible, sections = sections)
        }
    }

    // ── Filtri (come su Android) ────────────────────────────────────────────

    private fun updateFilter(debounceMs: Long = 0L, change: (CollectionFilter) -> CollectionFilter) {
        uiState = uiState.copy(filter = change(uiState.filter))
        scheduleRecompute(debounceMs)
    }

    fun updateSearchQuery(query: String) {
        // Con debounce: ogni tasto non deve rifiltrare l'intera collezione.
        updateFilter(SEARCH_DEBOUNCE_MS) { it.copy(query = query) }
    }

    fun setCategory(category: CardCategory) = updateFilter {
        // I tipi esistono solo sui Pokemon: scegliendo Allenatori o Energie
        // restavano attivi e svuotavano la lista.
        val keepTypes = category == CardCategory.ALL || category == CardCategory.POKEMON
        it.copy(category = category, types = if (keepTypes) it.types else emptySet())
    }

    fun toggleType(type: String) = updateFilter { it.copy(types = it.types.toggle(type)) }
    fun toggleRarity(rarity: String) = updateFilter { it.copy(rarities = it.rarities.toggle(rarity)) }
    fun toggleExpansion(expansion: String) = updateFilter { it.copy(expansions = it.expansions.toggle(expansion)) }
    fun toggleVariant(variant: String) = updateFilter { it.copy(variants = it.variants.toggle(variant)) }
    fun toggleLanguage(language: String) = updateFilter { it.copy(languages = it.languages.toggle(language)) }
    fun toggleValue(bucket: ValueBucket) = updateFilter { it.copy(values = it.values.toggle(bucket)) }
    fun setOnlyDuplicates(only: Boolean) = updateFilter { it.copy(onlyDuplicates = only) }

    /** Azzera i filtri, non la ricerca: quella si vede e si cancella da sola. */
    fun clearFilters() = updateFilter { CollectionFilter(query = it.query) }

    /** Tutto, ricerca compresa: dallo stato "nessun risultato". */
    fun clearFiltersAndSearch() = updateFilter { CollectionFilter() }

    private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

    // ── Ordinamento e vista ─────────────────────────────────────────────────

    fun setSort(sort: CollectionSort) {
        if (sort == uiState.sort) return
        uiState = uiState.copy(sort = sort)
        scheduleRecompute()
    }

    fun setExpansionOrder(order: ExpansionOrder) {
        if (order == uiState.expansionOrder) return
        uiState = uiState.copy(expansionOrder = order)
        scheduleRecompute()
    }

    fun setLayout(layout: CollectionLayout) {
        if (layout != uiState.layout) uiState = uiState.copy(layout = layout)
    }

    fun toggleViewMode() {
        uiState = uiState.copy(isGridView = !uiState.isGridView)
    }

    fun toggleGridColumns() {
        val next = when (uiState.gridColumns) { 2 -> 3; 3 -> 4; 4 -> 5; 5 -> 6; else -> 2 }
        uiState = uiState.copy(gridColumns = next)
    }

    fun clearMessages() {
        uiState = uiState.copy(errorMessage = null, successMessage = null)
    }

    // ── Scritture ───────────────────────────────────────────────────────────

    /** Le copie di una stampa: a zero la stampa si toglie. */
    fun setQuantity(print: PokemonCard, quantity: Int) = write { writer.setQuantity(print, quantity) }

    fun deletePrint(print: PokemonCard) = write { writer.deletePrint(print) }

    /** Il pannello del voto: a chi lo chiude null se e' andato, se no il motivo. */
    fun saveGrading(print: PokemonCard, isGraded: Boolean, grade: Float?, company: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val error = try {
                writer.setGrading(print, isGraded, grade, company)
                null
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                e.message ?: ""
            }
            onDone(error)
        }
    }

    private fun write(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                uiState = uiState.copy(errorMessage = "Non salvato: ${e.message}")
            }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}
