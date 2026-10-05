package com.emabuia.pokevault.ui.components

import com.emabuia.pokevault.data.formatAmount

/** Come formatEur in LabComponents dell'app Android: "€ 12,40". */
internal fun formatEur(value: Double): String = "€ " + formatAmount(value)

/** formatEurCompact di Android: "—" senza prezzo, niente centesimi sopra i cento euro. */
internal fun formatEurCompact(value: Double): String = when {
    value <= 0.0 -> "—"
    // Mezzo euro in su, come String.format("%.0f") su Android (kotlin.math.round andrebbe al pari).
    value >= 100.0 -> "€ " + kotlin.math.floor(value + 0.5).toLong()
    else -> "€ " + formatAmount(value)
}
