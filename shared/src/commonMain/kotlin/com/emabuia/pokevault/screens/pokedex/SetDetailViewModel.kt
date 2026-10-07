package com.emabuia.pokevault.screens.pokedex

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.CollectionRepository
import com.emabuia.pokevault.data.CollectionWriter
import com.emabuia.pokevault.data.NotSignedInException
import com.emabuia.pokevault.data.PokeWalletPriceData
import com.emabuia.pokevault.data.PokedexCatalog
import com.emabuia.pokevault.data.PriceEntry
import com.emabuia.pokevault.data.model.CardOptions
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.data.remote.TcgSet
import com.emabuia.pokevault.data.toLivePrices
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.hasPositiveEurPrice
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Lo stato di SetDetailViewModel di Android, con gli stessi campi. */
data class SetDetailUiState(
    val set: TcgSet? = null,
    val cards: List<TcgCard> = emptyList(),
    val ownedCardIds: Set<String> = emptySet(),
    /**
     * Le varianti possedute di ogni carta (id carta -> "Normal", "Reverse"...):
     * la griglia le usa per i badge sulla miniatura.
     */
    val ownedVariants: Map<String, Set<String>> = emptyMap(),
    val isLoading: Boolean = true,
    val isLoadingCards: Boolean = true,
    val isAddingCard: String? = null,
    val viewMode: String = "grid",
    val searchQuery: String = "",
    val translatedQuery: String = "",
    val showOnlyMissing: Boolean = false,
    val showOnlyOwned: Boolean = false,
    val selectedType: String? = null,
    val selectedSupertype: String? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val selectedCardPokeWalletPrices: PokeWalletPriceData? = null,
    val isLoadingPokeWalletPrices: Boolean = false,
) {
    val ownedCount: Int get() = cards.count { it.id in ownedCardIds }
    val totalCount: Int get() = cards.size
    val displayTotal: Int get() = if (cards.isNotEmpty()) cards.size else (set?.total ?: 0)
    val completionPercent: Int get() =
        if (displayTotal > 0) (ownedCount * 100 / displayTotal) else 0

    val availableTypes: List<String> get() = cards.flatMap { it.types ?: emptyList() }.distinct().sorted()
    val availableSupertypes: List<String> get() = cards.map { it.supertype }.distinct().sorted()
}

/**
 * Le carte di un'espansione: SetDetailViewModel di Android.
 *
 * Su Android i prezzi arrivavano da tre posti (snapshot italiano, carta
 * inglese "specchio", PokeWallet), carta per carta mentre la griglia scorreva.
 * Qui [PokedexCatalog] li porta gia' con le carte, dal Worker: le funzioni che
 * li chiedevano ([ensureCardPrice], [loadPokeWalletPrices]) restano con lo
 * stesso nome per la schermata, ma leggono quello che c'e'.
 *
 * Le possedute si rileggono a ogni scrittura in collezione
 * ([CollectionRepository.changes]): su Android era il listener di Firestore.
 * La ricerca in inglese (TranslationService) non c'e': il catalogo e' solo
 * italiano.
 */
class SetDetailViewModel(
    private val pokedex: PokedexCatalog,
    private val collection: CollectionRepository,
    private val writer: CollectionWriter,
) : ViewModel() {

    var uiState by mutableStateOf(SetDetailUiState())
        private set

    private var currentSetId: String? = null
    private var currentSourceMacro: String? = null
    private var ownedCardsJob: Job? = null

    /** Le carte col prezzo, per chiave: la griglia le sovrappone a uiState.cards (Android). */
    val pricedCards = mutableStateMapOf<String, TcgCard>()

    private fun defaultCollectionLanguage(): String {
        return CardOptions.languageLabelForMacro(currentSourceMacro ?: uiState.set?.language)
            ?: CardOptions.LANGUAGES.first()
    }

    fun loadSet(setId: String, sourceMacro: String? = null) {
        val normalizedMacro = sourceMacro?.trim()?.uppercase()
        if (currentSetId == setId && currentSourceMacro == normalizedMacro) return
        currentSetId = setId
        currentSourceMacro = normalizedMacro
        pricedCards.clear()

        uiState = uiState.copy(
            isLoading = true,
            isLoadingCards = true,
            searchQuery = "",
            translatedQuery = "",
            showOnlyMissing = false,
            showOnlyOwned = false,
            selectedType = null,
            selectedSupertype = null,
            errorMessage = null,
        )

        viewModelScope.launch {
            val cardsDeferred = async { pokedex.getCardsBySet(setId) }
            val setDeferred = async { pokedex.getSetInfo(setId) }

            val cardsResult = cardsDeferred.await()
            val setResult = setDeferred.await()

            cardsResult
                .onSuccess { cards ->
                    val resolvedSet = setResult.getOrNull() ?: TcgSet(
                        id = setId,
                        name = cards.firstOrNull()?.set?.name ?: setId,
                        series = cards.firstOrNull()?.set?.series ?: "",
                    )
                    uiState = uiState.copy(
                        set = resolvedSet,
                        cards = cards,
                        isLoading = false,
                        isLoadingCards = false,
                    )
                    observeOwnedCards(currentCards = cards)
                }
                .onFailure { error ->
                    uiState = uiState.copy(isLoading = false, isLoadingCards = false, errorMessage = "Errore: ${error.message}")
                }
        }
    }

    private fun observeOwnedCards(currentCards: List<TcgCard>) {
        ownedCardsJob?.cancel()
        ownedCardsJob = viewModelScope.launch {
            collection.changes.collectLatest {
                val ownedCards = try {
                    collection.load()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    // Senza accesso non c'e' niente da segnare: non e' un errore.
                    if (e !is NotSignedInException) uiState = uiState.copy(errorMessage = AppLocale.ownedCardsLoadError)
                    return@collectLatest
                }
                val currentCardIds = currentCards.asSequence().map { it.id }.filter { it.isNotBlank() }.toSet()
                val ownedIds = ownedCards.asSequence()
                    .map { it.apiCardId }
                    .filter { it.isNotBlank() && it in currentCardIds }
                    .toSet()
                val ownedVariants = ownedCards.asSequence()
                    .filter { it.apiCardId.isNotBlank() && it.apiCardId in currentCardIds }
                    .filter { it.variant.isNotBlank() }
                    .groupBy({ it.apiCardId }, { it.variant })
                    .mapValues { (_, variants) -> variants.toSet() }
                uiState = uiState.copy(ownedCardIds = ownedIds, ownedVariants = ownedVariants)
            }
        }
    }

    fun updateSearchQuery(query: String) {
        uiState = uiState.copy(searchQuery = query, translatedQuery = "")
    }

    fun toggleShowOnlyMissing() {
        uiState = uiState.copy(showOnlyMissing = !uiState.showOnlyMissing, showOnlyOwned = false)
    }

    fun toggleShowOnlyOwned() {
        uiState = uiState.copy(showOnlyOwned = !uiState.showOnlyOwned, showOnlyMissing = false)
    }

    fun selectType(type: String?) {
        uiState = uiState.copy(selectedType = type)
    }

    fun selectSupertype(supertype: String?) {
        uiState = uiState.copy(selectedSupertype = supertype)
    }

    private fun priceEntryOf(card: TcgCard): PriceEntry? = priceEntryOfCard(priced(card))

    fun addCardWithDetails(tcgCard: TcgCard, variant: String, quantity: Int, condition: String, language: String) {
        viewModelScope.launch {
            uiState = uiState.copy(isAddingCard = tcgCard.id)
            val resolvedLanguage = language.ifBlank { defaultCollectionLanguage() }
            try {
                writer.addFromCatalog(
                    tcgCard.source,
                    tcgCard.set?.name ?: uiState.set?.name.orEmpty(),
                    priceEntryOf(tcgCard),
                    variant,
                    quantity,
                    condition,
                    resolvedLanguage,
                )
                uiState = uiState.copy(
                    successMessage = "${tcgCard.name} aggiunta!",
                    ownedCardIds = uiState.ownedCardIds + tcgCard.id,
                    // Il badge della variante deve comparire subito: la rilettura
                    // della collezione arriva un attimo dopo e riscrive lo stesso valore.
                    ownedVariants = uiState.ownedVariants.withVariant(tcgCard.id, variant),
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                uiState = uiState.copy(errorMessage = "Errore")
            }
            // L'evidenziazione resta un attimo, cosi' si nota.
            delay(350)
            uiState = uiState.copy(isAddingCard = null)
        }
    }

    fun addMultipleCards(cards: List<TcgCard>, preferredVariant: String) {
        viewModelScope.launch {
            val originalOwnedIds = uiState.ownedCardIds
            val originalOwnedVariants = uiState.ownedVariants
            val prepared = cards.map { tcgCard ->
                val availableVariants = CardOptions.getVariantsForCard(
                    tcgCard.tcgplayer?.prices?.keys ?: emptySet(), tcgCard.rarity, tcgCard.set?.releaseDate,
                )
                val actualVariant = if (preferredVariant in availableVariants) preferredVariant
                else availableVariants.firstOrNull() ?: "Holo"
                tcgCard to actualVariant
            }
            var optimisticVariants = originalOwnedVariants
            for ((card, variant) in prepared) optimisticVariants = optimisticVariants.withVariant(card.id, variant)
            uiState = uiState.copy(
                ownedCardIds = originalOwnedIds + prepared.map { it.first.id },
                ownedVariants = optimisticVariants,
            )

            var failed = 0
            for ((card, variant) in prepared) {
                try {
                    writer.addFromCatalog(
                        card.source,
                        card.set?.name ?: uiState.set?.name.orEmpty(),
                        priceEntryOf(card),
                        variant,
                        1,
                        "Near Mint",
                        defaultCollectionLanguage(),
                    )
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    failed++
                }
            }
            uiState = if (failed == 0) {
                uiState.copy(successMessage = "${prepared.size} carte aggiunte!")
            } else {
                // La rilettura della collezione rimette a posto i badge di quelle non entrate.
                collection.notifyChanged()
                uiState.copy(errorMessage = "Errore")
            }
        }
    }

    fun removeCard(tcgCard: TcgCard) {
        viewModelScope.launch {
            uiState = try {
                writer.deleteAllPrints(tcgCard.id)
                uiState.copy(successMessage = "${tcgCard.name} rimossa")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                uiState.copy(errorMessage = "Errore")
            }
        }
    }

    fun setViewMode(mode: String) { uiState = uiState.copy(viewMode = mode) }
    fun clearMessages() { uiState = uiState.copy(errorMessage = null, successMessage = null) }

    /**
     * I prezzi della carta aperta nel pannello. Su Android si chiedevano a
     * PokeWallet; qui sono gia' sulla carta, e se mancano si rilegge il file
     * prezzi dell'espansione.
     */
    fun loadPokeWalletPrices(card: TcgCard) {
        val fromCard = priceDataFromCard(priced(card))
        uiState = uiState.copy(selectedCardPokeWalletPrices = fromCard, isLoadingPokeWalletPrices = fromCard == null)
        if (fromCard != null) return
        viewModelScope.launch {
            val setId = card.set?.id ?: currentSetId
            val entry = setId?.let { id -> runCatching { pokedex.getSetPriceMap(id) }.getOrDefault(emptyMap())[card.number] }
            uiState = uiState.copy(
                selectedCardPokeWalletPrices = entry?.toLivePrices()?.takeIf { it.hasEurPrices },
                isLoadingPokeWalletPrices = false,
            )
        }
    }

    fun clearPokeWalletPrices() {
        uiState = uiState.copy(selectedCardPokeWalletPrices = null, isLoadingPokeWalletPrices = false)
    }

    /** La stessa carta, col prezzo se e' arrivato dopo (Android). */
    private fun priced(card: TcgCard): TcgCard = pricedCards[card.id] ?: card

    /** Su Android chiedeva il prezzo mentre la griglia scorreva: qui c'e' gia', non serve niente. */
    @Suppress("UNUSED_PARAMETER")
    fun ensureCardPrice(card: TcgCard) = Unit
}

/** Aggiunge una variante alla mappa delle possedute senza toccare le altre. */
private fun Map<String, Set<String>>.withVariant(cardId: String, variant: String): Map<String, Set<String>> {
    if (cardId.isBlank() || variant.isBlank()) return this
    return this + (cardId to ((this[cardId] ?: emptySet()) + variant))
}

/**
 * I prezzi della scheda carta presi da quelli che la carta ha gia' (dal
 * Worker, con le medie a 1/7/30 giorni); null se non ha un prezzo in euro.
 * Serve al Pokedex e alla pagina illustratore.
 */
internal fun priceDataFromCard(card: TcgCard): PokeWalletPriceData? {
    val prices = card.cardmarket?.prices ?: return null
    if (!prices.hasPositiveEurPrice()) return null
    return PokeWalletPriceData(
        eurAvg = prices.averageSellPrice,
        eurLow = prices.lowPrice,
        eurTrend = prices.trendPrice,
        eurAvg1 = prices.avg1,
        eurAvg7 = prices.avg7,
        eurAvg30 = prices.avg30,
        cardMarketUrl = card.cardmarket?.url,
        tcgPlayerUrl = card.tcgplayer?.url,
    )
}

/** Il prezzo da salvare in collezione, dai prezzi Cardmarket della carta. */
internal fun priceEntryOfCard(card: TcgCard): PriceEntry? = card.cardmarket?.prices?.let {
    PriceEntry(avg = it.averageSellPrice, low = it.lowPrice, trend = it.trendPrice)
}
