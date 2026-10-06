package com.emabuia.pokevault.data.trade

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * La posizione APPROSSIMATIVA per TradeRadar: data/trade/CoarseLocation.kt di
 * Android. Una sola lettura, con l'app aperta. Al server va solo la cella
 * [Geohash] di 5 caratteri (~5 km): le coordinate restano sul telefono, dove
 * servono solo a dire quanto e' lontano un luogo d'incontro.
 */
interface CoarseLocationSource {
    /** null finche' l'utente non ha risposto alla domanda del sistema. */
    fun permission(): Boolean?

    /** Chiede il permesso (una volta sola: poi si passa dalle Impostazioni). */
    suspend fun requestPermission(): Boolean

    /** Latitudine e longitudine approssimative, o null se non arrivano entro [timeoutMs]. */
    suspend fun currentPoint(timeoutMs: Long = 15_000): Pair<Double, Double>?
}

expect fun platformCoarseLocation(): CoarseLocationSource

object CoarseLocation {

    /** La cella geohash di 5 caratteri, o null se la posizione non arriva. */
    suspend fun currentCell(source: CoarseLocationSource, timeoutMs: Long = 15_000): String? =
        source.currentPoint(timeoutMs)?.let { (lat, lon) -> Geohash.encode(lat, lon) }

    /** Distanza in km fra due punti (formula dell'emisenoverso). */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = kotlin.math.PI / 180
        val dLat = (lat2 - lat1) * r
        val dLon = (lon2 - lon1) * r
        val h = sin(dLat / 2).let { it * it } +
            cos(lat1 * r) * cos(lat2 * r) * sin(dLon / 2).let { it * it }
        return 6371 * 2 * asin(sqrt(h))
    }
}
