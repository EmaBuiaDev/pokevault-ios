package com.emabuia.pokevault.ui.theme

import androidx.compose.runtime.Composable
import platform.UIKit.UIAccessibilityIsReduceMotionEnabled

// Letto all'apertura dell'app: cambiarlo da Impostazioni con l'app aperta vale dal prossimo avvio.
@Composable
actual fun rememberAppMotion(): PokeVaultMotion =
    if (UIAccessibilityIsReduceMotionEnabled()) ReducedPokeVaultMotion else StandardPokeVaultMotion
