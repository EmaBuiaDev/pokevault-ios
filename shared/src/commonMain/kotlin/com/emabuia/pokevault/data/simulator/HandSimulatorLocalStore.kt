package com.emabuia.pokevault.data.simulator

import com.emabuia.pokevault.data.FileCache
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Una mano problematica salvata: data/simulator/HandSimulatorLocalStore.kt
 * di Android. Resta sul telefono come li' (non va su Firestore), quindi le
 * mani salvate su Android non si vedono qui e viceversa.
 */
@Serializable
data class SavedProblemHand(
    val id: String,
    val deckId: String,
    val deckName: String,
    val cards: List<String>,
    val tags: List<String>,
    val note: String,
    val createdAtMillis: Long
)

/**
 * Le mani salvate e le prove gratuite del simulatore, nei dati dell'app: al
 * posto delle SharedPreferences di Android.
 */
@OptIn(ExperimentalTime::class)
class HandSimulatorLocalStore(
    private val store: FileCache,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val serializer = ListSerializer(SavedProblemHand.serializer())

    fun getSavedHands(deckId: String? = null): List<SavedProblemHand> =
        getSavedHandsRaw()
            .filter { hand -> deckId.isNullOrBlank() || hand.deckId == deckId }
            .sortedByDescending { it.createdAtMillis }

    @OptIn(ExperimentalUuidApi::class)
    fun saveProblemHand(
        deckId: String,
        deckName: String,
        cards: List<String>,
        tags: List<String>,
        note: String = ""
    ) {
        if (deckId.isBlank() || cards.isEmpty()) return

        val current = getSavedHandsRaw() + SavedProblemHand(
            id = Uuid.random().toString(),
            deckId = deckId,
            deckName = deckName,
            cards = cards,
            tags = tags.distinct(),
            note = note,
            createdAtMillis = now()
        )

        // Solo le ultime 200, come su Android: il file non cresce senza fine.
        store.write(KEY_SAVED_HANDS, serializer, current.sortedByDescending { it.createdAtMillis }.take(200))
    }

    fun deleteProblemHand(id: String) {
        if (id.isBlank()) return
        store.write(KEY_SAVED_HANDS, serializer, getSavedHandsRaw().filterNot { it.id == id })
    }

    /** getHandSimulatorRuns di PremiumManager: le analisi gia' fatte su questo mazzo senza Premium. */
    fun runsFor(deckId: String): Int =
        if (deckId.isBlank()) 0 else store.read(runsKey(deckId), Int.serializer())?.data ?: 0

    /** consumeHandSimulatorRun. */
    fun consumeRun(deckId: String) {
        if (deckId.isBlank()) return
        store.write(runsKey(deckId), Int.serializer(), runsFor(deckId) + 1)
    }

    private fun getSavedHandsRaw(): List<SavedProblemHand> =
        store.read(KEY_SAVED_HANDS, serializer)?.data.orEmpty()

    private fun runsKey(deckId: String) = "hand_sim_runs_$deckId"

    private companion object {
        const val KEY_SAVED_HANDS = "saved_problem_hands"
    }
}

