package com.emabuia.pokevault.ui.components

import com.emabuia.pokevault.data.formatAmount

/** Come formatEur in LabComponents dell'app Android: "€ 12,40". */
internal fun formatEur(value: Double): String = "€ " + formatAmount(value)
