package com.emabuia.pokevault.data

import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.model.collectionCardKey
import com.emabuia.pokevault.firebase.FirestoreApi
import io.ktor.utils.io.CancellationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** I totali in cima alla collezione: CollectionStats di FirestoreRepository su Android. */
data class CollectionStats(
    val totalCards: Int = 0,
    val uniqueCards: Int = 0,
    val totalValue: Double = 0.0,
    val mostValuable: String = "-",
)

class NotSignedInException : Exception("Nessun accesso")

/**
 * La collezione dell'utente, in sola lettura.
 *
 * L'app Android, leggendo, corregge anche vecchi codici espansione e prezzi
 * mancanti scrivendo su Firestore (CollectionViewModel). Qui no: finche' le
 * scritture non hanno i loro test, l'app iOS la collezione la guarda soltanto.
 */
class CollectionRepository(
    private val firestore: FirestoreApi,
    private val auth: AuthRepository,
    private val cache: FileCache,
) {
    // isLenient: documenti scritti da versioni vecchie dell'app possono avere un
    // numero salvato come testo ("hp":"110"); meglio leggerlo che perdere la carta.
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    /** Quante carte dell'ultimo caricamento non si sono potute leggere: si dice, non si nasconde. */
    var unreadable: Int = 0
        private set
    private val serializer = ListSerializer(PokemonCard.serializer())

    /** L'ultima collezione scaricata per questo account, per mostrarla subito. */
    fun cached(uid: String): List<PokemonCard>? = cache.read(key(uid), serializer)?.data?.owned()

    /**
     * La collezione da Firestore. Se la rete non risponde, quella salvata
     * sul telefono; solo senza nessuna delle due l'errore arriva a chi chiama.
     */
    suspend fun load(): List<PokemonCard> {
        val session = auth.session.value ?: throw NotSignedInException()
        val token = auth.validIdToken() ?: throw NotSignedInException()
        return try {
            val documents = firestore.listCards(session.uid, token)
            val cards = documents.mapNotNull { (id, fields) ->
                runCatching { json.decodeFromJsonElement(PokemonCard.serializer(), fields).copy(id = id) }.getOrNull()
            }
            unreadable = documents.size - cards.size
            cache.write(key(session.uid), serializer, cards)
            cards.owned()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            cached(session.uid) ?: throw e
        }
    }

    /**
     * Via le carte solo-deck: stanno nella stessa collection ma non sono
     * possedute. Stesso filtro, client-side, di FirestoreRepository.getCards()
     * su Android (un whereEqualTo non troverebbe i documenti senza il campo).
     */
    private fun List<PokemonCard>.owned() = filter { !it.deckOnly }

    private fun key(uid: String) = "collection_$uid"

    companion object {
        /** Come CollectionViewModel su Android: "Carte Uniche" e' per carta, non per stampa. */
        fun stats(cards: List<PokemonCard>) = CollectionStats(
            totalCards = cards.sumOf { it.quantity },
            uniqueCards = cards.map { it.collectionCardKey() }.toSet().size,
            totalValue = cards.sumOf { it.estimatedValue * it.quantity },
        )
    }
}
