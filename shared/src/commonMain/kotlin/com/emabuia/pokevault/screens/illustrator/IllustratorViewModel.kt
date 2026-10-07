package com.emabuia.pokevault.screens.illustrator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.AuthRepository
import com.emabuia.pokevault.data.CollectionRepository
import com.emabuia.pokevault.data.CollectionWriter
import com.emabuia.pokevault.data.IllustratorRepository
import com.emabuia.pokevault.data.PokeWalletPriceData
import com.emabuia.pokevault.data.PokedexCatalog
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.screens.pokedex.priceDataFromCard
import com.emabuia.pokevault.screens.pokedex.priceEntryOfCard
import com.emabuia.pokevault.util.IllustratorEntry
import com.emabuia.pokevault.util.IllustratorRow
import com.emabuia.pokevault.util.Illustrators
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    /** Le carte dell'artista come nel Pokedex: col set (nome, data di uscita) e i prezzi. */
    val detailCards: List<TcgCard> = emptyList(),
    val isDetailLoading: Boolean = false,
    /** Le stampe possedute di ogni carta, per i badge sulla miniatura. */
    val ownedVariants: Map<String, Set<String>> = emptyMap(),
    val isAddingCard: String? = null,
    /** Prezzi della carta aperta nella scheda. */
    val sheetPrices: PokeWalletPriceData? = null,
    val isSheetPriceLoading: Boolean = false,
) {
    val rows: List<IllustratorRow> by lazy { Illustrators.rows(entries, ownedApiIds, followedKeys) }

    fun rowFor(key: String): IllustratorRow? = rows.firstOrNull { it.key == key }

    fun isOwned(cardId: String): Boolean = cardId.trim() in ownedApiIds
}

/**
 * IllustratorViewModel dell'app Android: l'indice del catalogo incrociato con
 * la collezione, ricalcolato dal vivo. Dalla pagina di un artista le carte si
 * aggiungono come dalla pagina di un'espansione: aggiunta rapida sulla tessera
 * o scheda carta.
 */
class IllustratorViewModel(
    private val repository: IllustratorRepository,
    private val collection: CollectionRepository,
    private val pokedex: PokedexCatalog,
    private val writer: CollectionWriter,
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
                ownedVariants = owned.asSequence()
                    .filter { card -> card.apiCardId.isNotBlank() && card.variant.isNotBlank() }
                    .groupBy({ card -> card.apiCardId.trim() }, { card -> card.variant })
                    .mapValues { (_, variants) -> variants.toSet() },
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
            // Le carte del Pokedex, coi prezzi del Worker: cosi' la griglia e la
            // scheda sono quelle della pagina espansione.
            val ids = cards.mapNotNull { it.italianId() }
            val byId = runCatching { pokedex.getCardsByIds(ids) }.getOrDefault(emptyMap())
            if (loadedDetailKey != key) return@launch
            _state.update { it.copy(isDetailLoading = false, detailCards = ids.mapNotNull { id -> byId[id] }) }
        }
    }

    /** I prezzi della carta aperta nella scheda; null chiude la scheda. Le carte li hanno gia'. */
    fun loadSheetPrices(card: TcgCard?) {
        _state.update { it.copy(sheetPrices = card?.let(::priceDataFromCard), isSheetPriceLoading = false) }
    }

    /**
     * Aggiunge una carta dalla pagina di un illustratore, come dalla pagina di
     * un'espansione. Il prezzo e' il minimo di Cardmarket, come ovunque.
     */
    fun addCard(card: TcgCard, variant: String, quantity: Int, condition: String, language: String) {
        viewModelScope.launch {
            _state.update { it.copy(isAddingCard = card.id) }
            try {
                writer.addFromCatalog(
                    card.source,
                    card.set?.name.orEmpty(),
                    priceEntryOfCard(card),
                    variant,
                    quantity,
                    condition,
                    language.ifBlank { "🇮🇹 Italiano" },
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
            // Il bordo resta visibile un attimo: senza, il riscontro del tocco
            // passa inosservato su una griglia da tre colonne.
            delay(350)
            _state.update { it.copy(isAddingCard = null) }
        }
    }

    /** Come nella pagina espansione: lascia stare le copie solo-deck. */
    fun removeCard(card: TcgCard) {
        viewModelScope.launch {
            try {
                writer.deleteAllPrints(card.id)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }
}
