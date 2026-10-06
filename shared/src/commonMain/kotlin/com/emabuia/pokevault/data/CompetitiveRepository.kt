package com.emabuia.pokevault.data

import com.emabuia.pokevault.data.model.Deck
import com.emabuia.pokevault.data.model.MatchLog
import com.emabuia.pokevault.data.model.Tournament
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
import kotlinx.serialization.json.JsonPrimitive

/**
 * La sezione Competitive: users/{uid}/decks, tournaments e match_logs, gli
 * stessi documenti e campi che scrive FirestoreRepository su Android.
 */
class CompetitiveRepository(
    private val firestore: FirestoreApi,
    private val auth: AuthRepository,
    private val cache: FileCache,
    private val writes: FirestoreWrites,
) {
    // Cresce a ogni scrittura: le schermate aperte lo osservano e ricaricano.
    private val _changes = MutableStateFlow(0)
    val changes: StateFlow<Int> = _changes.asStateFlow()

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    // ── Mazzi ───────────────────────────────────────────────────────────────

    /** getDecks su Android: dal piu' recente. */
    suspend fun decks(): List<Deck> =
        read(DECKS, Deck.serializer())
            .sortedByDescending { it.createdAt?.seconds ?: Long.MIN_VALUE }

    /**
     * saveDeck su Android: id vuoto crea, altrimenti riscrive il documento
     * intero (set()). Anche createdAt torna "adesso" a ogni salvataggio, come
     * li': e' quello che tiene in cima all'elenco l'ultimo deck toccato.
     */
    suspend fun saveDeck(deck: Deck): String = write { uid, token ->
        val fields = linkedMapOf<String, Any?>(
            "name" to deck.name,
            "cards" to deck.cards,
            "mainTypes" to deck.mainTypes,
            "averageHp" to deck.averageHp,
            "totalCards" to deck.totalCards,
            "recommendedEnergy" to deck.recommendedEnergy,
            "coverImageUrl" to deck.coverImageUrl,
            "coverImageUrls" to deck.displayCoverImageUrls(),
            // set() riscrive il documento intero: senza questo campo, modificare
            // un deck di prova lo farebbe tornare un deck normale.
            "deckOnly" to deck.deckOnly,
            "createdAt" to ServerNow,
        )
        if (deck.id.isEmpty()) {
            writes.createDocument(uid, token, DECKS, fields)
        } else {
            writes.setDocument(uid, token, DECKS, deck.id, fields)
            deck.id
        }
    }

    suspend fun deleteDeck(deckId: String) = write { uid, token ->
        writes.deleteDocuments(uid, token, DECKS, listOf(deckId))
    }

    /**
     * Le carte solo-deck che non servono piu' a nessun deck. Non toccano i
     * totali del profilo: entrando non li avevano toccati (deleteCard su Android).
     */
    suspend fun deleteDeckOnlyCards(cardIds: List<String>) {
        if (cardIds.isEmpty()) return
        write { uid, token -> writes.deleteDocuments(uid, token, CARDS, cardIds) }
    }

    // ── Tornei ──────────────────────────────────────────────────────────────

    /**
     * getTournaments su Android ordina per "date" sul server, e cosi' esclude
     * i tornei senza data; qui si ordina dopo, e non ne resta fuori nessuno.
     */
    suspend fun tournaments(): List<Tournament> =
        read(TOURNAMENTS, Tournament.serializer())
            .sortedByDescending { it.date?.seconds ?: Long.MIN_VALUE }

    /**
     * saveTournament: id vuoto crea, altrimenti riscrive il documento intero
     * come set() su Android. createdAt resta quello del torneo.
     */
    suspend fun saveTournament(tournament: Tournament): String = write { uid, token ->
        val fields = linkedMapOf<String, Any?>(
            "location" to tournament.location,
            "date" to (tournament.date ?: ServerNow),
            "participants" to tournament.participants,
            "registrationFee" to tournament.registrationFee,
            "type" to tournament.type,
            "format" to tournament.format,
            "deckName" to tournament.deckName,
            "deckId" to tournament.deckId,
            "createdAt" to (tournament.createdAt ?: ServerNow),
        )
        if (tournament.id.isEmpty()) {
            writes.createDocument(uid, token, TOURNAMENTS, fields)
        } else {
            writes.setDocument(uid, token, TOURNAMENTS, tournament.id, fields)
            tournament.id
        }
    }

    /** deleteTournament: prima le sue partite, poi il torneo. */
    suspend fun deleteTournament(tournamentId: String) {
        val matchIds = matchLogs().filter { it.tournamentId == tournamentId }.map { it.id }
        write { uid, token ->
            writes.deleteDocuments(uid, token, MATCHES, matchIds)
            writes.deleteDocuments(uid, token, TOURNAMENTS, listOf(tournamentId))
        }
    }

    // ── Partite ─────────────────────────────────────────────────────────────

    /** getMatchLogs: tutte, dalla piu' recente (ordinate qui, come su Android). */
    suspend fun matchLogs(): List<MatchLog> =
        read(MATCHES, MatchLog.serializer())
            .sortedByDescending { it.createdAt?.seconds ?: Long.MIN_VALUE }

    /** saveMatchLog, con le stesse regole di saveTournament. */
    suspend fun saveMatchLog(match: MatchLog): String = write { uid, token ->
        val fields = linkedMapOf<String, Any?>(
            "tournamentId" to match.tournamentId,
            "round" to match.round,
            "result" to match.result,
            "opponentName" to match.opponentName,
            "opponentDeck" to match.opponentDeck,
            "notes" to match.notes,
            "createdAt" to (match.createdAt ?: ServerNow),
        )
        if (match.id.isEmpty()) {
            writes.createDocument(uid, token, MATCHES, fields)
        } else {
            writes.setDocument(uid, token, MATCHES, match.id, fields)
            match.id
        }
    }

    suspend fun deleteMatchLog(matchId: String) = write { uid, token ->
        writes.deleteDocuments(uid, token, MATCHES, listOf(matchId))
    }

    // ── In comune ───────────────────────────────────────────────────────────

    /** Dalla rete, e su disco per quando la rete non c'e'. Un documento illeggibile si salta. */
    private suspend fun <T> read(collection: String, serializer: KSerializer<T>): List<T> {
        val uid = auth.session.value?.uid ?: throw NotSignedInException()
        val token = auth.validIdToken() ?: throw NotSignedInException()
        val key = "${collection}_$uid"
        val list = ListSerializer(serializer)
        return try {
            firestore.listDocuments(uid, token, collection)
                .mapNotNull { (id, fields) ->
                    runCatching {
                        val withId = JsonObject(fields + ("id" to JsonPrimitive(id)))
                        json.decodeFromJsonElement(serializer, withId)
                    }.getOrNull()
                }
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
        private const val DECKS = "decks"
        private const val TOURNAMENTS = "tournaments"
        private const val MATCHES = "match_logs"
        private const val CARDS = "cards"
    }
}
