package com.emabuia.pokevault.data.simulator

import com.emabuia.pokevault.data.FileCache
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Le mani salvate e le prove gratuite restano sul telefono, fra un avvio e l'altro. */
class HandSimulatorLocalStoreTest {
    private val dir = Path(SystemTemporaryDirectory, "pokevault-sim-${Random.nextLong().toULong()}").toString()
    private var clock = 1_000L

    @AfterTest
    fun cleanUp() {
        if (!SystemFileSystem.exists(Path(dir))) return
        SystemFileSystem.list(Path(dir)).forEach { SystemFileSystem.delete(it) }
        SystemFileSystem.delete(Path(dir))
    }

    private fun store() = HandSimulatorLocalStore(FileCache(dir), now = { clock })

    @Test
    fun savedHandsSurviveARestartNewestFirstAndPerDeck() {
        store().saveProblemHand("d1", "Dragapult", listOf("Dreepy", "Ultra Ball"), listOf("NO_ENERGY_T1", "NO_ENERGY_T1"))
        clock += 1
        store().saveProblemHand("d2", "Gardevoir", listOf("Ralts"), listOf("NO_OUT_T1"))
        clock += 1
        store().saveProblemHand("d1", "Dragapult", listOf("Duskull"), emptyList())

        val restarted = store()
        assertEquals(listOf(listOf("Duskull"), listOf("Dreepy", "Ultra Ball")), restarted.getSavedHands("d1").map { it.cards })
        assertEquals(listOf("NO_ENERGY_T1"), restarted.getSavedHands("d1").last().tags)
        assertEquals(3, restarted.getSavedHands().size)

        restarted.deleteProblemHand(restarted.getSavedHands("d2").single().id)
        assertTrue(store().getSavedHands("d2").isEmpty())
    }

    @Test
    fun anEmptyHandOrNoDeckIsNotSaved() {
        store().saveProblemHand("", "Senza", listOf("Pikachu"), emptyList())
        store().saveProblemHand("d1", "Vuota", emptyList(), emptyList())
        assertTrue(store().getSavedHands().isEmpty())
    }

    @Test
    fun freeRunsAreCountedPerDeck() {
        assertEquals(0, store().runsFor("d1"))
        store().consumeRun("d1")
        assertEquals(1, store().runsFor("d1"))
        assertEquals(0, store().runsFor("d2"))
    }
}
