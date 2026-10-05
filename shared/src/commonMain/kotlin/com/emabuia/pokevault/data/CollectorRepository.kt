package com.emabuia.pokevault.data

import com.emabuia.pokevault.data.model.Album
import com.emabuia.pokevault.data.model.GoalAlbum
import com.emabuia.pokevault.firebase.FirestoreApi
import com.emabuia.pokevault.firebase.FirestoreWrites
import com.emabuia.pokevault.firebase.ServerNow
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Album e chase del Collector Lab: users/{uid}/albums e users/{uid}/goal_albums,
 * gli stessi documenti e campi che scrive FirestoreRepository su Android.
 */
class CollectorRepository(
    private val firestore: FirestoreApi,
    private val auth: AuthRepository,
    private val cache: FileCache,
    private val writes: FirestoreWrites,
) {
    // Cresce a ogni scrittura: le schermate aperte lo osservano e ricaricano.
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    // ── Album ───────────────────────────────────────────────────────────────

    /** getAlbums su Android: dal piu' recente. */
    suspend fun albums(): List<Album> =
        read(ALBUMS, Album.serializer()) { fields, id -> json.decodeFromJsonElement(Album.serializer(), fields).copy(id = id) }
            .sortedByDescending { it.createdAt?.seconds ?: Long.MIN_VALUE }

    /** saveAlbum con id vuoto: tutti i campi di Android, carte comprese. */
    suspend fun createAlbum(album: Album): String = write { uid, token ->
        writes.createDocument(uid, token, ALBUMS, linkedMapOf(
            "name" to album.name,
            "description" to album.description,
            "pokemonType" to album.pokemonType,
            "expansion" to album.expansion,
            "supertype" to album.supertype,
            "size" to album.size,
            "theme" to album.theme,
            "cardIds" to album.cardIds,
            "coverImageUrl" to album.coverImageUrl,
            "createdAt" to ServerNow,
        ))
    }

    /**
     * Le impostazioni di un album esistente. Su Android saveAlbum riscrive il
     * documento intero; qui si toccano solo questi campi, cosi' carte,
     * copertina e data non si possono perdere modificandolo.
     */
    suspend fun updateAlbum(album: Album) = write { uid, token ->
        writes.updateDocument(uid, token, ALBUMS, album.id, linkedMapOf(
            "name" to album.name,
            "description" to album.description,
            "pokemonType" to album.pokemonType,
            "expansion" to album.expansion,
            "supertype" to album.supertype,
            "size" to album.size,
            "theme" to album.theme,
        ))
    }

    suspend fun deleteAlbum(id: String) = write { uid, token -> writes.deleteDocuments(uid, token, ALBUMS, listOf(id)) }

    /** addCardsToAlbum: una scrittura sola, arrayUnion. */
    suspend fun addCardsToAlbum(id: String, cardIds: List<String>) = write { uid, token ->
        writes.changeArray(uid, token, ALBUMS, id, "cardIds", cardIds, add = true)
    }

    suspend fun removeCardFromAlbum(id: String, cardId: String) = write { uid, token ->
        writes.changeArray(uid, token, ALBUMS, id, "cardIds", listOf(cardId), add = false)
    }

    /** setAlbumCardIds: l'ordine e' quello dell'array, quindi si riscrive intero. */
    suspend fun setAlbumCardIds(id: String, cardIds: List<String>) = write { uid, token ->
        writes.updateDocument(uid, token, ALBUMS, id, mapOf("cardIds" to cardIds))
    }

    suspend fun updateAlbumCover(id: String, coverImageUrl: String) = write { uid, token ->
        writes.updateDocument(uid, token, ALBUMS, id, mapOf("coverImageUrl" to coverImageUrl))
    }

    // ── Chase ───────────────────────────────────────────────────────────────

    suspend fun goalAlbums(): List<GoalAlbum> =
        read(GOALS, GoalAlbum.serializer()) { fields, id -> json.decodeFromJsonElement(GoalAlbum.serializer(), fields).copy(id = id) }
            .sortedByDescending { it.createdAt?.seconds ?: Long.MIN_VALUE }

    /** saveGoalAlbum con id vuoto. */
    suspend fun createGoalAlbum(album: GoalAlbum): String = write { uid, token ->
        writes.createDocument(uid, token, GOALS, linkedMapOf(
            "name" to album.name,
            "criteriaType" to album.criteriaType,
            "criteriaValue" to album.criteriaValue,
            "targetCardApiIds" to album.targetCardApiIds,
            "createdAt" to ServerNow,
        ))
    }

    suspend fun deleteGoalAlbum(id: String) = write { uid, token -> writes.deleteDocuments(uid, token, GOALS, listOf(id)) }

    // ── In comune ───────────────────────────────────────────────────────────

    /** Dalla rete, e su disco per quando la rete non c'e'. Un documento illeggibile si salta. */
    private suspend fun <T> read(collection: String, serializer: KSerializer<T>, decode: (JsonObject, String) -> T): List<T> {
        val uid = auth.session.value?.uid ?: throw NotSignedInException()
        val token = auth.validIdToken() ?: throw NotSignedInException()
        val key = "${collection}_$uid"
        val list = ListSerializer(serializer)
        return try {
            firestore.listDocuments(uid, token, collection)
                .mapNotNull { (id, fields) -> runCatching { decode(fields, id) }.getOrNull() }
                .also { cache.write(key, list, it) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            cache.read(key, list)?.data ?: throw e
        }
    }

    private suspend fun <T> write(block: suspend (uid: String, token: String) -> T): T {
        val uid = auth.session.value?.uid ?: throw NotSignedInException()
        val token = auth.validIdToken() ?: throw NotSignedInException()
        // Anche una scrittura andata a meta' cambia i dati: si rilegge comunque.
        try {
            return block(uid, token)
        } finally {
            _changes.value += 1
        }
    }

    companion object {
        private const val ALBUMS = "albums"
        private const val GOALS = "goal_albums"
    }
}
