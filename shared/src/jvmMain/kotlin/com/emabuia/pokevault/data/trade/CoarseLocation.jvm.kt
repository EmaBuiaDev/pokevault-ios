package com.emabuia.pokevault.data.trade

/** Sul desktop, che serve solo a provare la UI, la posizione non c'e'. */
actual fun platformCoarseLocation(): CoarseLocationSource = object : CoarseLocationSource {
    override fun permission(): Boolean = false
    override suspend fun requestPermission(): Boolean = false
    override suspend fun currentPoint(timeoutMs: Long): Pair<Double, Double>? = null
}
