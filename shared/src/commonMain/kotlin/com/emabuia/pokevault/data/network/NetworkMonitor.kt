package com.emabuia.pokevault.data.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * C'e' rete o no, per tutta l'app: NetworkMonitor di Android.
 *
 * Serve al banner "sei offline". Nel dubbio vale online: un banner "offline"
 * sbagliato spaventa, e le letture se la cavano comunque da sole con le copie
 * sul telefono.
 */
object NetworkMonitor {

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private var started = false

    /** Una volta sola per processo; le chiamate dopo la prima non fanno niente. */
    fun start() {
        if (started) return
        started = true
        watchPlatformNetwork { online -> _isOnline.value = online }
    }
}

/**
 * Avvisa a ogni cambio di rete. Su iOS e' NWPathMonitor; altrove (Android e
 * desktop, solo per lo sviluppo) non fa niente e l'app resta "online".
 */
internal expect fun watchPlatformNetwork(onChange: (Boolean) -> Unit)
