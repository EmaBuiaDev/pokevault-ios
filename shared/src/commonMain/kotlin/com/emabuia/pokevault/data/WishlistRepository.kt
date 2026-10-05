package com.emabuia.pokevault.data

import com.emabuia.pokevault.data.model.Wishlist
import com.emabuia.pokevault.firebase.FirestoreApi
import com.emabuia.pokevault.firebase.FirestoreWrites
import com.emabuia.pokevault.firebase.ServerNow
import com.emabuia.pokevault.data.model.WishlistDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import io.ktor.utils.io.CancellationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Una carta in wishlist, col prezzo minimo del suo set (se il set ha prezzi). */
data class WishlistCard(
    val card: Card,
    val price: PriceEntry?,
    /** L'id com'e' scritto nella lista: quello che arrayRemove deve ritrovare. */
    val storedId: String,
)

/** Una wishlist con le carte che su iOS si sanno mostrare. */
data class WishlistContent(
    val wishlist: Wishlist,
    val cards: List<WishlistCard>,
    /** Carte salvate con id PokeWallet o vecchi: su Android ci sono, qui no. */
    val unresolved: Int,
) {
    val totalValue: Double get() = cards.sumOf { it.price?.lowOrAverage() ?: 0.0 }
}

/**
 * Le wishlist dell'account: users/{uid}/wishlists, come le
 * scrive l'app Android. Dalla piu' recente, come getWishlists() su Android.
 */
class WishlistRepository(
    private val firestore: FirestoreApi,
    private val auth: AuthRepository,
    private val catalog: CatalogRepository,
    private val cache: FileCache,
    private val writes: FirestoreWrites,
) {
    // Cresce a ogni scrittura: le schermate aperte lo osservano e ricaricano.
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
    private val serializer = ListSerializer(Wishlist.serializer())

    suspend fun wishlists(): List<Wishlist> {
        val session = auth.session.value ?: throw NotSignedInException()
        val token = auth.validIdToken() ?: throw NotSignedInException()
        val key = "wishlists_${session.uid}"
        val lists = try {
            firestore.listWishlists(session.uid, token).mapNotNull { (id, fields) ->
                runCatching { json.decodeFromJsonElement(Wishlist.serializer(), fields).copy(id = id) }.getOrNull()
            }.also { cache.write(key, serializer, it) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            cache.read(key, serializer)?.data ?: throw e
        }
        return lists.sortedByDescending { it.createdAt?.seconds ?: Long.MIN_VALUE }
    }

    // ── Scritture, come FirestoreRepository su Android ──────────────────────

    /** saveWishlist con id vuoto: una lista nuova, coi campi di Android. */
    suspend fun create(draft: WishlistDraft, firstCardIds: List<String> = emptyList()): String = write { uid, token ->
        writes.createDocument(uid, token, "wishlists", linkedMapOf(
            "name" to draft.name,
            "iconKey" to draft.iconKey,
            "accentKey" to draft.accentKey,
            "budgetEur" to draft.budgetEur,
            "cardIds" to firstCardIds,
            "createdAt" to ServerNow,
        ))
    }

    /**
     * Nome, icona, colore e budget. Su Android saveWishlist riscrive tutto il
     * documento; qui si toccano solo questi campi, cosi' le carte della lista
     * non si possono perdere modificandola.
     */
    suspend fun update(id: String, draft: WishlistDraft) = write { uid, token ->
        writes.updateDocument(uid, token, "wishlists", id, linkedMapOf(
            "name" to draft.name,
            "iconKey" to draft.iconKey,
            "accentKey" to draft.accentKey,
            "budgetEur" to draft.budgetEur,
        ))
    }

    suspend fun delete(id: String) = write { uid, token -> writes.deleteDocuments(uid, token, "wishlists", listOf(id)) }

    /** removeCardFromWishlist (arrayRemove). */
    suspend fun removeCard(listId: String, cardId: String) = write { uid, token ->
        writes.changeArray(uid, token, "wishlists", listId, "cardIds", listOf(cardId), add = false)
    }

    /**
     * updateCardWishlists su Android: la carta entra nelle liste spuntate ed
     * esce da quelle tolte. Si prova ogni lista anche se una fallisce, poi si
     * dice quante non sono andate.
     */
    suspend fun moveCard(cardId: String, addTo: Collection<String>, removeFrom: Collection<String>) = write { uid, token ->
        val failed = (addTo.map { it to true } + removeFrom.map { it to false }).count { (listId, add) ->
            try {
                writes.changeArray(uid, token, "wishlists", listId, "cardIds", listOf(cardId), add = add)
                false
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                true
            }
        }
        if (failed > 0) throw IllegalStateException("$failed wishlist non aggiornate")
    }

    private suspend fun <T> write(block: suspend (uid: String, token: String) -> T): T {
        val uid = auth.session.value?.uid ?: throw NotSignedInException()
        val token = auth.validIdToken() ?: throw NotSignedInException()
        // Anche una scrittura andata a meta' cambia le liste: si rilegge comunque.
        try {
            return block(uid, token)
        } finally {
            _changes.value += 1
        }
    }

    /** Le carte di una lista, nell'ordine in cui sono state aggiunte. */
    suspend fun content(wishlist: Wishlist): WishlistContent {
        val found = catalog.italianCardsById(wishlist.cardIds)
        val cards = wishlist.cardIds.mapNotNull { id -> found[id]?.let { id to it } }.map { (id, card) ->
            val price = runCatching { catalog.expansionCards(card.espansioneId).priceOf(card) }.getOrNull()
            WishlistCard(card, price, storedId = id)
        }
        return WishlistContent(wishlist, cards, unresolved = wishlist.cardIds.size - cards.size)
    }
}

/** Il numero che sommano i totali: il minimo, o la media se il minimo manca (in euro). */
fun PriceEntry.lowOrAverage(): Double? = low?.takeIf { it > 0 } ?: avg?.takeIf { it > 0 }
