package com.emabuia.pokevault.data

import com.emabuia.pokevault.data.model.Wishlist
import com.emabuia.pokevault.firebase.FirestoreApi
import io.ktor.utils.io.CancellationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Una carta in wishlist, col prezzo minimo del suo set (se il set ha prezzi). */
data class WishlistCard(val card: Card, val price: PriceEntry?)

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
 * Le wishlist dell'account, in sola lettura: users/{uid}/wishlists, come le
 * scrive l'app Android. Dalla piu' recente, come getWishlists() su Android.
 */
class WishlistRepository(
    private val firestore: FirestoreApi,
    private val auth: AuthRepository,
    private val catalog: CatalogRepository,
    private val cache: FileCache,
) {
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

    /** Le carte di una lista, nell'ordine in cui sono state aggiunte. */
    suspend fun content(wishlist: Wishlist): WishlistContent {
        val found = catalog.italianCardsById(wishlist.cardIds)
        val cards = wishlist.cardIds.mapNotNull { found[it] }.map { card ->
            val price = runCatching { catalog.expansionCards(card.espansioneId).priceOf(card) }.getOrNull()
            WishlistCard(card, price)
        }
        return WishlistContent(wishlist, cards, unresolved = wishlist.cardIds.size - cards.size)
    }
}

/** Il numero che sommano i totali: il minimo, o la media se il minimo manca (in euro). */
fun PriceEntry.lowOrAverage(): Double? = low?.takeIf { it > 0 } ?: avg?.takeIf { it > 0 }
