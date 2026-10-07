package com.emabuia.pokevault.testcompat

import kotlin.concurrent.Volatile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Quello che il server finto riceve, scritto dai thread di Ktor e letto dal
 * test su un altro thread. Con un ArrayList le due cose si pestavano i piedi:
 * la lettura cadeva con ConcurrentModificationException, e due aggiunte
 * insieme potevano far lanciare il server finto, cioe' far fallire una
 * richiesta che il test aspettava (TradeRadarFlowTest, CI di e68fbd8).
 *
 * Le aggiunte passano una alla volta; chi legge prende una lista immutabile.
 */
class SharedLog<T> {
    private val mutex = Mutex()

    @Volatile
    private var items: List<T> = emptyList()

    /** Quello che e' arrivato fin qui: non cambia piu' sotto le mani. */
    val snapshot: List<T> get() = items

    suspend fun add(item: T) = mutex.withLock { items = items + item }

    suspend fun addAll(more: Collection<T>) = mutex.withLock { items = items + more }

    suspend fun clear() = mutex.withLock { items = emptyList() }
}
