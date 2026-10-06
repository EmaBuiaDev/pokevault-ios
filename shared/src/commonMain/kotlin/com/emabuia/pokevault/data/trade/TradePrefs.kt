package com.emabuia.pokevault.data.trade

import com.emabuia.pokevault.data.FileCache
import kotlinx.serialization.Serializable

/**
 * Le preferenze di TradeRadar: su Android le SharedPreferences "trade_radar"
 * ([TradeBadge.PREFS]), qui un file nei dati dell'app. Le legge anche la barra
 * per il numero sul tasto ([TradeBadge.refresh] vuole le chiusure applicate).
 *
 * Il permesso delle notifiche non c'e': su iOS TradeRadar non ha le push.
 */
@Serializable
data class TradePrefsData(
    val introSeen: Boolean = false,
    val levelsExplained: Boolean = false,
    /** Gli scambi chiusi di cui la collezione e' gia' stata aggiornata. */
    val appliedClosings: Set<String> = emptySet(),
    /** L'ultimo livello visto; null finche' non se n'e' visto nessuno (niente festa la prima volta). */
    val lastTier: String? = null,
)

class TradePrefs(private val store: FileCache) {

    fun read(): TradePrefsData = store.read(TradeBadge.PREFS, TradePrefsData.serializer())?.data ?: TradePrefsData()

    fun update(change: (TradePrefsData) -> TradePrefsData) {
        store.write(TradeBadge.PREFS, TradePrefsData.serializer(), change(read()))
    }
}
