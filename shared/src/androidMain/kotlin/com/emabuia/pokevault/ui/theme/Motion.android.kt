package com.emabuia.pokevault.ui.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/**
 * Impostazione di sistema che scala (o azzera) le animazioni.
 *
 * Compose applica gia' questo fattore alle durate dei `tween` attraverso il suo
 * frame clock; quello che non sa fare da solo e' *saltare* le animazioni
 * decorative, ed e' il motivo per cui il tema legge il valore anche qui.
 */
internal const val ANIMATOR_DURATION_SCALE_SETTING: String =
    Settings.Global.ANIMATOR_DURATION_SCALE

private fun readAnimatorScale(context: Context): Float =
    Settings.Global.getFloat(context.contentResolver, ANIMATOR_DURATION_SCALE_SETTING, 1f)

/**
 * Palette di motion da fornire al tema: [StandardPokeVaultMotion], oppure
 * [ReducedPokeVaultMotion] se l'utente ha azzerato le animazioni di sistema.
 *
 * L'impostazione e' osservata e non letta una volta sola: si cambia dalle
 * Opzioni sviluppatore senza che l'app venga ricreata, e senza observer l'app
 * continuerebbe ad animare fino al riavvio del processo.
 */
@Composable
actual fun rememberAppMotion(): PokeVaultMotion {
    // Le anteprime non hanno un contentResolver di sistema da interrogare.
    if (LocalInspectionMode.current) return StandardPokeVaultMotion

    val context = LocalContext.current
    var scale by remember(context) { mutableFloatStateOf(readAnimatorScale(context)) }

    DisposableEffect(context) {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                scale = readAnimatorScale(context)
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(ANIMATOR_DURATION_SCALE_SETTING),
            false,
            observer
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }

    return if (scale == 0f) ReducedPokeVaultMotion else StandardPokeVaultMotion
}
