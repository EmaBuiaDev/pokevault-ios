package com.emabuia.pokevault.screens.wishlist

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.CollectionRepository
import com.emabuia.pokevault.data.PokedexCatalog
import com.emabuia.pokevault.data.PremiumRepository
import com.emabuia.pokevault.data.WishlistRepository
import com.emabuia.pokevault.data.model.Wishlist
import com.emabuia.pokevault.data.model.WishlistAccents
import com.emabuia.pokevault.data.model.WishlistDraft
import com.emabuia.pokevault.data.model.WishlistIcons
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.WishlistLab
import com.emabuia.pokevault.util.WishlistRow
import com.emabuia.pokevault.util.WishlistSummary
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * Le wishlist: WishlistViewModel dell'app Android, con le stesse proprieta' e
 * le stesse funzioni, cosi' le schermate portate da li' restano quelle.
 *
 * Su iOS le liste e la collezione si rileggono a ogni scrittura
 * ([WishlistRepository.changes], [CollectionRepository.changes]): su Android
 * erano i listener di Firestore. Le carte arrivano dal catalogo italiano in un
 * colpo solo ([PokedexCatalog.getCardsByIds]), coi prezzi. Il Premium lo dice
 * [PremiumRepository] ([isPremium]): su Android era PremiumManager.
 */
class WishlistViewModel(
    private val repository: WishlistRepository,
    private val collection: CollectionRepository,
    private val pokedex: PokedexCatalog,
    private val premium: PremiumRepository,
) : ViewModel() {

    companion object {
        const val FREE_WISHLIST_LIMIT = PremiumRepository.FREE_WISHLIST_LIMIT

        fun isValidWishlistName(name: String): Boolean {
            val normalized = name.trim()
            return normalized.isNotEmpty() && normalized.length <= 40
        }

        fun normalizeIconKey(iconKey: String): String = WishlistIcons.normalize(iconKey)

        fun canCreateWishlistCount(isPremium: Boolean, currentCount: Int): Boolean {
            return isPremium || currentCount < FREE_WISHLIST_LIMIT
        }

        /** Il budget scritto a mano: accetta sia "12,50" sia "12.50", zero se vuoto. */
        fun parseBudget(raw: String): Double {
            val normalized = raw.trim().replace(',', '.').replace("€", "").trim()
            if (normalized.isEmpty()) return 0.0
            return normalized.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
        }

        fun normalizeDraft(draft: WishlistDraft): WishlistDraft {
            // L'accento si risolve sulla chiave *originale*: una lista modificata
            // senza toccare il colore tiene il colore che aveva.
            return draft.copy(
                name = draft.name.trim(),
                iconKey = WishlistIcons.normalize(draft.iconKey),
                accentKey = WishlistAccents.normalize(draft.accentKey, draft.iconKey),
                budgetEur = draft.budgetEur.coerceAtLeast(0.0),
            )
        }
    }

    var wishlists by mutableStateOf<List<Wishlist>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isSaving by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var successMessage by mutableStateOf<String?>(null)
        private set

    /** PremiumManager.isPremium su Android. Finche' il server non risponde non si blocca nessuno. */
    var isPremium by mutableStateOf(true)
        private set

    /** Le carte delle wishlist, man mano che arrivano dal catalogo. */
    private val cardCache = mutableStateMapOf<String, TcgCard>()

    /** Gli id che il catalogo non sa risolvere: non si richiedono all'infinito. */
    private val unresolvedCardIds = mutableSetOf<String>()
    private val pendingCardIds = mutableSetOf<String>()

    var isLoadingCards by mutableStateOf(false)
        private set

    /** Gli id delle carte gia' in collezione, per sapere cosa e' gia' stato preso. */
    var ownedCardIds by mutableStateOf<Set<String>>(emptySet())
        private set

    val cardsById: Map<String, TcgCard> get() = cardCache

    init {
        loadWishlists()
        loadOwnedCards()
        viewModelScope.launch { isPremium = premium.isPremium() != false }
    }

    private fun loadWishlists() {
        viewModelScope.launch {
            isLoading = true
            repository.changes.collectLatest {
                try {
                    val list = repository.wishlists()
                    wishlists = list
                    isLoading = false
                    ensureCardsLoaded(list.flatMap { it.cardIds })
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    isLoading = false
                    errorMessage = "Errore nel caricamento wishlist"
                }
            }
        }
    }

    private fun loadOwnedCards() {
        viewModelScope.launch {
            collection.changes.collectLatest {
                val cards = try {
                    collection.load()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    return@collectLatest
                }
                ownedCardIds = WishlistLab.ownedCardIds(cards)
            }
        }
    }

    /** Indice di tutti i cardId in wishlist, ricalcolato solo quando le wishlist cambiano. */
    private val wishlistedCardIds: Set<String> by derivedStateOf {
        wishlists.flatMapTo(HashSet()) { it.cardIds }
    }

    fun isCardWishlisted(cardId: String): Boolean = cardId in wishlistedCardIds

    fun canCreateWishlist(isPremium: Boolean): Boolean {
        return canCreateWishlistCount(isPremium, wishlists.size)
    }

    fun getWishlistById(wishlistId: String): Wishlist? {
        return wishlists.firstOrNull { it.id == wishlistId }
    }

    fun getWishlistIdsForCard(cardId: String): Set<String> {
        return wishlists.asSequence()
            .filter { cardId in it.cardIds }
            .map { it.id }
            .toSet()
    }

    // ── Carte ─────────────────────────────────────────────────────────────

    /** Chiede al catalogo le carte che non sono gia' in cache (tutte insieme). */
    private fun ensureCardsLoaded(cardIds: List<String>) {
        val toLoad = cardIds.asSequence()
            .distinct()
            .filter { it.isNotBlank() && it !in cardCache && it !in pendingCardIds && it !in unresolvedCardIds }
            .toList()
        if (toLoad.isEmpty()) return

        pendingCardIds += toLoad
        isLoadingCards = true

        viewModelScope.launch {
            val found = pokedex.getCardsByIds(toLoad)
            toLoad.forEach { id ->
                val card = found[id]
                if (card != null) cardCache[id] = card else unresolvedCardIds += id
                pendingCardIds -= id
            }
            isLoadingCards = pendingCardIds.isNotEmpty()
        }
    }

    /** Le carte di una lista, quelle gia' arrivate. */
    fun cardsOf(wishlistId: String): List<TcgCard> {
        val wishlist = getWishlistById(wishlistId) ?: return emptyList()
        return wishlist.cardIds.mapNotNull { cardCache[it] }
    }

    /** true quando di quella lista manca ancora qualche carta da caricare. */
    fun isLoadingCardsOf(wishlistId: String): Boolean {
        val wishlist = getWishlistById(wishlistId) ?: return false
        return wishlist.cardIds.any { it in pendingCardIds }
    }

    fun rows(): List<WishlistRow> = WishlistLab.rows(wishlists, cardCache, ownedCardIds)

    fun summary(): WishlistSummary = WishlistLab.summary(wishlists, cardCache, ownedCardIds)

    // ── Carte dentro le liste ─────────────────────────────────────────────

    /** Il Result.isSuccess di Android: vero se la scrittura e' andata. */
    private suspend fun attempt(block: suspend () -> Unit): Boolean = try {
        block()
        true
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        false
    }

    fun removeCardFromWishlist(wishlistId: String, cardId: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            if (attempt { repository.removeCard(wishlistId, cardId) }) {
                successMessage = "Carta rimossa dalla lista"
                onResult(true)
            } else {
                errorMessage = "Impossibile rimuovere la carta"
                onResult(false)
            }
        }
    }

    /** Toglie dalla lista le carte che nel frattempo sono entrate in collezione. */
    fun removeOwnedCards(wishlistId: String, onResult: (Boolean) -> Unit = {}) {
        val wishlist = getWishlistById(wishlistId)
        if (wishlist == null) {
            errorMessage = AppLocale.wishlistNotFound
            onResult(false)
            return
        }
        val owned = wishlist.cardIds.filter { it in ownedCardIds }
        if (owned.isEmpty()) {
            onResult(true)
            return
        }
        viewModelScope.launch {
            if (attempt { owned.forEach { repository.removeCard(wishlistId, it) } }) {
                successMessage = AppLocale.wishlistCleanupDone(owned.size)
                onResult(true)
            } else {
                errorMessage = "Impossibile rimuovere le carte"
                onResult(false)
            }
        }
    }

    fun addCardToWishlist(wishlistId: String, cardId: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            if (attempt { repository.addCards(listOf(wishlistId), listOf(cardId)) }) {
                successMessage = "Carta aggiunta alla wishlist"
                onResult(true)
            } else {
                errorMessage = "Impossibile aggiungere la carta"
                onResult(false)
            }
        }
    }

    /** Lo stesso blocco di carte in piu' liste: una scrittura per lista (il Chase). */
    fun addCardsToWishlists(
        wishlistIds: Set<String>,
        cardIds: List<String>,
        onResult: (Boolean) -> Unit = {},
    ) {
        if (wishlistIds.isEmpty() || cardIds.isEmpty()) {
            onResult(false)
            return
        }
        viewModelScope.launch {
            val failed = supervisorScope {
                wishlistIds
                    .map { id -> async { !attempt { repository.addCards(listOf(id), cardIds) } } }
                    .awaitAll()
                    .any { it }
            }
            if (failed) {
                errorMessage = AppLocale.chaseWishlistError
                onResult(false)
            } else {
                successMessage = AppLocale.chaseWishlistAdded(cardIds.size)
                onResult(true)
            }
        }
    }

    fun updateCardWishlists(cardId: String, targetWishlistIds: Set<String>, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val currentWishlistIds = getWishlistIdsForCard(cardId)
            val toAdd = targetWishlistIds - currentWishlistIds
            val toRemove = currentWishlistIds - targetWishlistIds

            if (toAdd.isEmpty() && toRemove.isEmpty()) {
                onResult(true)
                return@launch
            }

            val failures = supervisorScope {
                val addResults = toAdd.map { id -> async { !attempt { repository.addCards(listOf(id), listOf(cardId)) } } }
                val removeResults = toRemove.map { id -> async { !attempt { repository.removeCard(id, cardId) } } }
                (addResults + removeResults).awaitAll().count { it }
            }

            if (failures == 0) {
                successMessage = "Wishlist aggiornate"
                onResult(true)
            } else {
                errorMessage = "Alcune wishlist non sono state aggiornate"
                onResult(false)
            }
        }
    }

    fun removeCardFromAllWishlists(cardId: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val lists = getWishlistIdsForCard(cardId)
            if (attempt { lists.forEach { repository.removeCard(it, cardId) } }) {
                successMessage = "Carta rimossa dalla wishlist"
                onResult(true)
            } else {
                errorMessage = "Impossibile rimuovere la carta"
                onResult(false)
            }
        }
    }

    // ── Liste ─────────────────────────────────────────────────────────────

    fun createWishlistAndAddCard(
        draft: WishlistDraft,
        cardId: String,
        isPremium: Boolean,
        onResult: (Boolean) -> Unit = {},
    ) {
        val normalized = validatedDraft(draft, isPremium) ?: run { onResult(false); return }

        viewModelScope.launch {
            isSaving = true
            // La lista nasce gia' con la carta dentro: una scrittura sola.
            if (attempt { repository.create(normalized, firstCardIds = listOf(cardId)) }) {
                successMessage = "Wishlist creata e carta aggiunta"
                onResult(true)
            } else {
                errorMessage = "Impossibile creare wishlist"
                onResult(false)
            }
            isSaving = false
        }
    }

    fun createWishlist(draft: WishlistDraft, isPremium: Boolean, onResult: (Boolean) -> Unit = {}) {
        val normalized = validatedDraft(draft, isPremium) ?: run { onResult(false); return }

        viewModelScope.launch {
            isSaving = true
            if (attempt { repository.create(normalized) }) {
                successMessage = "Wishlist creata"
                onResult(true)
            } else {
                errorMessage = "Impossibile creare wishlist"
                onResult(false)
            }
            isSaving = false
        }
    }

    fun updateWishlistDetails(
        wishlistId: String,
        draft: WishlistDraft,
        onResult: (Boolean) -> Unit = {},
    ) {
        val normalized = normalizeDraft(draft)
        if (!isValidWishlistName(normalized.name)) {
            errorMessage = "Nome lista non valido"
            onResult(false)
            return
        }
        if (getWishlistById(wishlistId) == null) {
            errorMessage = AppLocale.wishlistNotFound
            onResult(false)
            return
        }

        viewModelScope.launch {
            isSaving = true
            if (attempt { repository.update(wishlistId, normalized) }) {
                successMessage = AppLocale.wishlistUpdated
                onResult(true)
            } else {
                errorMessage = AppLocale.wishlistUpdateFailed
                onResult(false)
            }
            isSaving = false
        }
    }

    fun deleteWishlist(wishlistId: String) {
        viewModelScope.launch {
            if (!attempt { repository.delete(wishlistId) }) errorMessage = "Impossibile eliminare la wishlist"
        }
    }

    fun clearMessages() {
        errorMessage = null
        successMessage = null
    }

    /** Normalizza e controlla limite free e nome; null se non si puo' procedere. */
    private fun validatedDraft(draft: WishlistDraft, isPremium: Boolean): WishlistDraft? {
        if (!canCreateWishlistCount(isPremium, wishlists.size)) {
            errorMessage = AppLocale.premiumWishlistLimitMessage
            return null
        }
        val normalized = normalizeDraft(draft)
        if (!isValidWishlistName(normalized.name)) {
            errorMessage = "Nome lista non valido"
            return null
        }
        return normalized
    }
}
