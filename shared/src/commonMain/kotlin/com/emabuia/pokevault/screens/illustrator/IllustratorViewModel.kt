package com.emabuia.pokevault.screens.illustrator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.AuthRepository
import com.emabuia.pokevault.data.CatalogRepository
import com.emabuia.pokevault.data.Card
import com.emabuia.pokevault.data.CollectionRepository
import com.emabuia.pokevault.data.ExpansionsState
import com.emabuia.pokevault.data.IllustratorRepository
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.util.IllustratorEntry
import com.emabuia.pokevault.util.IllustratorRow
import com.emabuia.pokevault.util.Illustrators
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Le carte di un artista in un set: i gruppi della pagina illustratore. */
data class IllustratorSetGroup(val setId: String, val setName: String, val cards: List<Card>)

data class IllustratorUiState(
    // Parte a true: al primo frame si sta gia' caricando, o la lista lampeggerebbe "nessun artista".
    val isLoading: Boolean = true,
    val entries: List<IllustratorEntry> = emptyList(),
    val cardsWithoutIllustrator: Int = 0,
    val signedIn: Boolean = false,
    val ownedApiIds: Set<String> = emptySet(),
    /** Carte in collezione che non vengono dal catalogo italiano: non contano per nessuno. */
    val nonItalianOwnedCount: Int = 0,
    val followedKeys: Set<String> = emptySet(),
    val detailCards: List<Card> = emptyList(),
    val isDetailLoading: Boolean = false,
    /** id espansione -> (nome, data di uscita), per i gruppi della pagina artista. */
    val expansionInfo: Map<String, Pair<String, String>> = emptyMap(),
) {
    val rows: List<IllustratorRow> by lazy { Illustrators.rows(entries, ownedApiIds, followedKeys) }

    fun rowFor(key: String): IllustratorRow? = rows.firstOrNull { it.key == key }

    fun isOwned(card: Card): Boolean = card.italianId()?.let { it in ownedApiIds } == true
}

/**
 * IllustratorViewModel dell'app Android: l'indice del catalogo incrociato con
 * la collezione, ricalcolato dal vivo. Le carte si aggiungono dal dettaglio
 * carta, come dal resto dell'app iOS; qui non c'e' l'aggiunta rapida.
 */
class IllustratorViewModel(
    private val repository: IllustratorRepository,
    private val collection: CollectionRepository,
    private val catalog: CatalogRepository,
    private val auth: AuthRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(IllustratorUiState())
    val state: StateFlow<IllustratorUiState> = _state.asStateFlow()

    private var loadedDetailKey: String? = null

    init {
        loadIndex()
        viewModelScope.launch { loadOwned() }
        viewModelScope.launch { loadFollowed() }
        // Una carta aggiunta dal dettaglio sposta subito le barre che la riguardano.
        viewModelScope.launch { collection.changes.drop(1).collect { loadOwned() } }
        viewModelScope.launch { repository.followChanges.drop(1).collect { loadFollowed() } }
    }

    fun loadIndex() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val index = runCatching { repository.index() }.getOrNull()
            _state.update {
                it.copy(
                    isLoading = false,
                    entries = index?.entries.orEmpty(),
                    cardsWithoutIllustrator = index?.cardsWithoutIllustrator ?: 0,
                )
            }
            loadedDetailKey?.let { key -> loadedDetailKey = null; loadDetail(key) }
        }
    }

    private suspend fun loadOwned() {
        val signedIn = auth.session.value != null
        val cards: List<PokemonCard> = if (!signedIn) emptyList() else try {
            collection.load()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            auth.session.value?.uid?.let { collection.cached(it) }.orEmpty()
        }
        val owned = cards.filter { it.quantity >= 1 }
        _state.update {
            it.copy(
                signedIn = signedIn,
                ownedApiIds = owned.mapTo(HashSet()) { card -> card.apiCardId.trim() },
                nonItalianOwnedCount = owned.count { card -> !card.apiCardId.trim().startsWith("ita:") },
            )
        }
    }

    private suspend fun loadFollowed() {
        val keys = try {
            repository.followed()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return
        }
        _state.update { it.copy(followedKeys = keys) }
    }

    /** Ottimistico come su Android: la stellina risponde senza aspettare la rete. */
    fun toggleFollow(key: String) {
        if (!_state.value.signedIn) return
        val nowFollowed = key !in _state.value.followedKeys
        _state.update { it.copy(followedKeys = if (nowFollowed) it.followedKeys + key else it.followedKeys - key) }
        viewModelScope.launch {
            // Se la scrittura non va, la rilettura dopo followChanges rimette le cose a posto.
            runCatching { repository.setFollowed(key, nowFollowed) }
        }
    }

    /** Le carte di un artista. Se l'indice non c'e' ancora, riparte quando arriva. */
    fun loadDetail(key: String) {
        if (loadedDetailKey == key) return
        loadedDetailKey = key
        val entry = _state.value.entries.firstOrNull { it.key == key } ?: return
        viewModelScope.launch {
            _state.update { it.copy(isDetailLoading = true, detailCards = emptyList()) }
            val cards = runCatching { repository.cards(entry) }.getOrDefault(emptyList())
            if (loadedDetailKey != key) return@launch
            _state.update { it.copy(isDetailLoading = false, detailCards = cards) }
            if (_state.value.expansionInfo.isEmpty()) loadExpansionInfo()
        }
    }

    private suspend fun loadExpansionInfo() {
        runCatching { catalog.ensureExpansions() }
        val expansions = (catalog.expansions.value as? ExpansionsState.Ready)?.expansions.orEmpty()
        _state.update { state ->
            state.copy(expansionInfo = expansions.associate { it.id.lowercase() to (it.name to it.releaseDate.orEmpty()) })
        }
    }

    companion object {
        /**
         * I gruppi in ordine cronologico, dal set piu' recente: i set di un
         * artista raccontano la sua carriera solo se messi in fila per data.
         */
        fun groups(cards: List<Card>, info: Map<String, Pair<String, String>>): List<IllustratorSetGroup> =
            cards.groupBy { it.espansioneId.lowercase() }
                .entries
                .sortedWith(compareByDescending<Map.Entry<String, List<Card>>> { info[it.key]?.second.orEmpty() }.thenBy { it.key })
                .map { (setId, setCards) ->
                    IllustratorSetGroup(
                        setId = setId,
                        setName = info[setId]?.first?.takeIf { it.isNotBlank() } ?: setId.uppercase(),
                        cards = setCards.sortedWith(Card.byNumber),
                    )
                }
    }
}
