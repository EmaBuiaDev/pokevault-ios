package com.emabuia.pokevault.data.trade

/**
 * L'app Android di questo repo serve solo a provare il codice comune:
 * TradeRadar vero su Android e' quello dell'app PokeVault di Play.
 */
actual fun platformCoarseLocation(): CoarseLocationSource = object : CoarseLocationSource {
    override fun permission(): Boolean = false
    override suspend fun requestPermission(): Boolean = false
    override suspend fun currentPoint(timeoutMs: Long): Pair<Double, Double>? = null
}
