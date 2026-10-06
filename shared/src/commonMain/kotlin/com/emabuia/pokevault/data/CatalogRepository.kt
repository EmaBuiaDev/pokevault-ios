package com.emabuia.pokevault.data

import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

sealed interface ExpansionsState {
    data object Loading : ExpansionsState
    data class Ready(val expansions: List<Expansion>) : ExpansionsState
    data class Error(val message: String) : ExpansionsState
}

/** Le carte di un'espansione, in ordine di numero, con i prezzi per numero. */
data class ExpansionCards(
    val cards: List<Card>,
    val prices: Map<String, PriceEntry>,
) {
    fun priceOf(card: Card): PriceEntry? = card.number?.let { prices[it] }
}

/**
 * Catalogo: rete, poi memoria per la sessione, poi disco per i riavvii.
 *
 * Il disco serve a due cose: aprire l'app senza aspettare la rete, e
 * mostrare quello che si e' gia' visto quando la rete non c'e'. Per questo un
 * errore di rete con qualcosa su disco non e' mai un errore a schermo.
 */
class CatalogRepository(
    private val api: CatalogApi,
    private val cache: FileCache? = null,
) {
    private val _expansions = MutableStateFlow<ExpansionsState>(ExpansionsState.Loading)
    val expansions: StateFlow<ExpansionsState> = _expansions.asStateFlow()

    private val expansionsMutex = Mutex()

    // In memoria per la sessione, come getExpansionCards() su Android.
    private val cardsMemory = mutableMapOf<String, ExpansionCards>()

    /** Home e Pokedex chiedono lo stesso elenco: lo scarica solo il primo. */
    suspend fun ensureExpansions() = expansionsMutex.withLock {
        if (_expansions.value !is ExpansionsState.Ready) refresh()
    }

    suspend fun refresh() {
        if (_expansions.value !is ExpansionsState.Ready) {
            // Quello su disco subito, poi la rete lo aggiorna.
            _expansions.value = cache?.read(KEY_EXPANSIONS, EXPANSIONS)
                ?.let { ExpansionsState.Ready(newestFirst(it.data)) }
                ?: ExpansionsState.Loading
        }
        try {
            val fresh = api.getExpansions()
            cache?.write(KEY_EXPANSIONS, EXPANSIONS, fresh)
            _expansions.value = ExpansionsState.Ready(newestFirst(fresh))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (_expansions.value !is ExpansionsState.Ready) {
                _expansions.value = ExpansionsState.Error(e.message ?: e::class.simpleName.orEmpty())
            }
        }
    }

    suspend fun expansionCards(expansionId: String): ExpansionCards {
        cardsMemory[expansionId]?.let { return it }
        val loaded = coroutineScope {
            val cards = async {
                cachedOrFetch("cards_$expansionId", CARDS, CARDS_TTL_MS) { api.getExpansionCards(expansionId) }
            }
            // Senza prezzi le carte si mostrano lo stesso: non e' un errore della schermata.
            val prices = async {
                try {
                    cachedOrFetch("prices_$expansionId", PRICES, PRICES_TTL_MS) { api.getExpansionPrices(expansionId) }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    emptyMap()
                }
            }
            ExpansionCards(cards.await().sortedWith(Card.byNumber), prices.await())
        }
        cardsMemory[expansionId] = loaded
        return loaded
    }

    // Il catalogo completo in memoria dopo il primo uso: 18.800 carte da
    // decodificare non si rifanno a ogni lettera digitata.
    private var fullCatalog: List<Card>? = null

    /**
     * Le carte che somigliano a [query] in tutto il catalogo (vedi
     * [CatalogSearch]). Il catalogo si scarica la prima volta e resta sul
     * telefono un giorno: dopo, la ricerca funziona anche offline.
     */
    suspend fun search(query: String): List<Card> = withContext(Dispatchers.Default) {
        // Tutto fuori dal thread dell'interfaccia: anche la prima lettura dal
        // telefono e' un JSON di 6 MB da decodificare.
        CatalogSearch.search(loadFullCatalog(), query)
    }

    /**
     * Le carte italiane salvate per id nelle wishlist dell'app Android
     * ("ita:me05:4": cartella del set e numero). Gli altri id (PokeWallet,
     * vecchi "sv3-125") qui non si risolvono: servirebbe PokeWallet, col suo
     * limite orario condiviso. Tornano fuori dalla mappa, e chi chiama li conta.
     */
    suspend fun italianCardsById(ids: Collection<String>): Map<String, Card> = withContext(Dispatchers.Default) {
        val wanted = ids.filter { it.startsWith("ita:") }.toSet()
        if (wanted.isEmpty()) return@withContext emptyMap()
        loadFullCatalog().asSequence()
            .mapNotNull { card -> card.italianId()?.takeIf { it in wanted }?.let { it to card } }
            .toMap()
    }

    /**
     * La carta che una riga di decklist indica (set, numero, nome): vedi
     * [ItalianCardLookup]. Per l'import dei deck.
     */
    suspend fun findExactItalianCard(
        setCode: String?,
        number: String?,
        expectedName: String? = null,
        requireNameMatch: Boolean = false,
    ): Card? = withContext(Dispatchers.Default) {
        ItalianCardLookup.findExact(loadFullCatalog(), setCode, number, expectedName, requireNameMatch)
    }

    /** Le carte che lo Scanner propone per una lettura: vedi [ScannerCatalogSearch]. */
    suspend fun scannerCandidates(
        name: String?,
        number: String?,
        setTotal: String?,
        targetSetId: String?,
        expansions: List<Expansion>,
        limit: Int,
    ): List<ScannerCatalogSearch.Hit> = withContext(Dispatchers.Default) {
        ScannerCatalogSearch.search(loadFullCatalog(), expansions, name, number, setTotal, targetSetId, limit)
    }

    private suspend fun loadFullCatalog(): List<Card> =
        fullCatalog ?: cachedOrFetch(KEY_CATALOG, CARDS, CATALOG_TTL_MS) { api.getFullCatalog() }
            .also { fullCatalog = it }

    /**
     * Il dato su disco se e' abbastanza recente; altrimenti la rete, e se la
     * rete non risponde il dato su disco comunque vecchio. Solo senza niente su
     * disco l'errore di rete arriva al chiamante.
     */
    private suspend fun <T> cachedOrFetch(
        key: String,
        serializer: kotlinx.serialization.KSerializer<T>,
        ttlMs: Long,
        fetch: suspend () -> T,
    ): T {
        val cached = cache?.read(key, serializer)
        if (cached != null && cache.ageMillis(cached) < ttlMs) return cached.data
        return try {
            fetch().also { cache?.write(key, serializer, it) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            cached?.data ?: throw e
        }
    }

    private fun newestFirst(expansions: List<Expansion>) =
        // Dalla piu' recente, come l'elenco dell'app Android.
        expansions.sortedByDescending { it.releaseDate.orEmpty() }

    companion object {
        private const val KEY_EXPANSIONS = "expansions"
        private const val KEY_CATALOG = "catalog_all"
        private val EXPANSIONS = ListSerializer(Expansion.serializer())
        private val CARDS = ListSerializer(Card.serializer())
        private val PRICES = MapSerializer(String.serializer(), PriceEntry.serializer())

        // Le carte di un set cambiano di rado; i prezzi 12 ore come l'app Android.
        private const val CARDS_TTL_MS = 24L * 60 * 60 * 1000
        private const val PRICES_TTL_MS = 12L * 60 * 60 * 1000
        private const val CATALOG_TTL_MS = 24L * 60 * 60 * 1000
    }
}
