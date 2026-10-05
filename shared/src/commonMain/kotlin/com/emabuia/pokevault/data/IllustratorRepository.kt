package com.emabuia.pokevault.data

import com.emabuia.pokevault.firebase.FirestoreApi
import com.emabuia.pokevault.firebase.FirestoreWrites
import com.emabuia.pokevault.firebase.ServerNow
import com.emabuia.pokevault.util.IllustratorEntry
import com.emabuia.pokevault.util.Illustrators
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLPathPart
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Una voce di `GET /v1/illustrators`: il nome GREZZO e le chiavi immagine delle sue carte. */
@Serializable
data class IllustratorRecord(
    val name: String = "",
    val cardCount: Int = 0,
    val expansionCount: Int = 0,
    val cardIds: List<String> = emptyList(),
)

@Serializable
data class IllustratorsResponse(
    val illustrators: List<IllustratorRecord> = emptyList(),
    /** Quante carte del catalogo non dicono chi le ha disegnate (~2%). */
    val cardsWithoutIllustrator: Int = 0,
)

@Serializable
private data class IllustratorCardsResponse(val cards: List<Card> = emptyList())

data class IllustratorIndex(val entries: List<IllustratorEntry>, val cardsWithoutIllustrator: Int)

/**
 * La sezione illustratori: l'indice e le carte di un artista dalle rotte del
 * Worker (come Android, non dal catalogo intero da 10 MB), e gli artisti
 * seguiti in users/{uid}/followed_illustrators, gli stessi documenti di Android.
 */
class IllustratorRepository(
    private val client: HttpClient,
    private val cache: FileCache,
    private val auth: AuthRepository,
    private val firestore: FirestoreApi,
    private val writes: FirestoreWrites,
    private val baseUrl: String = WORKER_BASE_URL,
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val indexMutex = Mutex()
    private var index: IllustratorIndex? = null
    private val cardsMemory = mutableMapOf<String, List<Card>>()

    // Cresce quando cambia un "segui": la lista e la pagina dell'artista si allineano.
    private val _followChanges = MutableStateFlow(0)
    val followChanges: StateFlow<Int> = _followChanges.asStateFlow()

    /**
     * L'indice raggruppato per persona. Su disco per un giorno come su Android
     * (ILLUSTRATORS_TTL_MS); se la rete non risponde vale anche quello vecchio.
     */
    suspend fun index(): IllustratorIndex = indexMutex.withLock {
        index?.let { return it }
        val cached = cache.read(KEY_INDEX, IllustratorsResponse.serializer())
        val response = if (cached != null && cache.ageMillis(cached) < INDEX_TTL_MS) {
            cached.data
        } else {
            try {
                parseIndex(client.get("${baseUrl.trimEnd('/')}/v1/illustrators").bodyAsText())
                    .also { cache.write(KEY_INDEX, IllustratorsResponse.serializer(), it) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                cached?.data ?: throw e
            }
        }
        val entries = Illustrators.entries(
            response.illustrators.map { it.name to it.cardIds },
            toApiId = { imageKey -> Card(cardId = imageKey).italianId() },
            toPreviewUrl = { imageKey -> Card(cardId = imageKey).imageUrl(baseUrl) },
        )
        IllustratorIndex(entries, response.cardsWithoutIllustrator).also { index = it }
    }

    /**
     * Le carte di un artista: una chiamata per ogni grafia grezza della voce
     * (quasi sempre una), perche' il Worker confronta il nome esatto.
     */
    suspend fun cards(entry: IllustratorEntry): List<Card> {
        cardsMemory[entry.key]?.let { return it }
        val base = baseUrl.trimEnd('/')
        val cards = entry.rawNames.flatMap { rawName ->
            val text = client.get("$base/v1/illustrators/${rawName.encodeURLPathPart()}/cards").bodyAsText()
            json.decodeFromString<IllustratorCardsResponse>(text.removePrefix(UTF8_BOM)).cards
        }
            // Come parseIllustratorCards su Android: niente righe senza id, set o nome.
            .filter { it.cardId.isNotBlank() && it.espansioneId.isNotBlank() && it.nome.isNotBlank() }
            .distinctBy { it.cardId }
        return cards.also { cardsMemory[entry.key] = it }
    }

    /** Le chiavi degli artisti seguiti; vuoto senza accesso. */
    suspend fun followed(): Set<String> {
        val uid = auth.session.value?.uid ?: return emptySet()
        val token = auth.validIdToken() ?: return emptySet()
        return firestore.listDocuments(uid, token, FOLLOWED).map { it.first }.toSet()
    }

    /** setIllustratorFollowed su Android: esistere e' il dato, con la sola data. */
    suspend fun setFollowed(key: String, followed: Boolean) {
        val id = key.trim()
        if (id.isEmpty()) return
        val uid = auth.session.value?.uid ?: throw NotSignedInException()
        val token = auth.validIdToken() ?: throw NotSignedInException()
        try {
            if (followed) {
                writes.setDocument(uid, token, FOLLOWED, id, mapOf("followedAt" to ServerNow))
            } else {
                writes.deleteDocuments(uid, token, FOLLOWED, listOf(id))
            }
        } finally {
            _followChanges.value += 1
        }
    }

    private fun parseIndex(text: String): IllustratorsResponse {
        val payload = json.decodeFromString<IllustratorsResponse>(text.removePrefix(UTF8_BOM).trim())
        // Come parseIllustrators su Android: niente voci senza nome o senza carte.
        return payload.copy(illustrators = payload.illustrators.filter { it.name.isNotBlank() && it.cardIds.isNotEmpty() })
    }

    companion object {
        private const val KEY_INDEX = "illustrators"
        private const val FOLLOWED = "followed_illustrators"
        private const val INDEX_TTL_MS = 24L * 60 * 60 * 1000
        private const val UTF8_BOM = "﻿"
    }
}
