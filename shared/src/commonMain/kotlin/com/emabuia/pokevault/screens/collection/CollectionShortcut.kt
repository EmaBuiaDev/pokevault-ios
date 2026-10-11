package com.emabuia.pokevault.screens.collection

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * "Vedi tutte" dalle carte recenti della Home: apre la Collezione con le
 * ultime aggiunte in cima e nessun filtro, come CollectionShortcut su Android.
 * La Home e la Collezione non condividono un ViewModel, quindi la richiesta
 * passa di qui e la Collezione la consuma quando compare.
 */
object CollectionShortcut {
    var pendingRecent by mutableStateOf(false)
        private set

    fun requestRecent() {
        pendingRecent = true
    }

    /** Vero una volta sola: chi la legge la spegne. */
    fun consumeRecent(): Boolean {
        val pending = pendingRecent
        pendingRecent = false
        return pending
    }
}

/**
 * Le carte nell'ordine in cui la collezione le mostra, per lo swipe nel
 * dettaglio. Fuori dalla rotta per lo stesso motivo di [CollectionShortcut]:
 * una lista di chiavi in una rotta non ci sta. Se il processo muore resta
 * vuota e il dettaglio semplicemente non scorre, come prima.
 */
object CollectionBrowse {
    var order: List<String> = emptyList()
}
