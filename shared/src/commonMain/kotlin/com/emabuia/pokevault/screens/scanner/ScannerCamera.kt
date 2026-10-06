package com.emabuia.pokevault.screens.scanner

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import com.emabuia.pokevault.ocr.ScannedFrame

/**
 * La fotocamera dello Scanner: l'unica parte che dipende dalla piattaforma.
 *
 * Su Android sono CameraX e ML Kit (ScannerScreen.kt li', CameraPreview e
 * CardFrameAnalyzer); su iOS AVFoundation e Apple Vision. In entrambi i casi
 * ogni fotogramma diventa un [ScannedFrame] con le stesse due letture: il
 * testo della carta inquadrata, con la posizione di ogni riga, e quello della
 * striscia in fondo, ingrandita, da cui esce il numero.
 */
@Composable
expect fun ScannerCameraPreview(
    onFrameScanned: (ScannedFrame) -> Unit,
    flashEnabled: Boolean,
    scanEnabled: Boolean,
    modifier: Modifier = Modifier,
)

/** Il permesso della fotocamera: chiesto una volta, poi solo dalle Impostazioni. */
class CameraAccess(
    /** null finche' non si sa (prima risposta del sistema). */
    val granted: Boolean?,
    /** Gia' negato: il sistema non lo richiede piu', si va nelle Impostazioni. */
    val deniedForever: Boolean,
    val request: () -> Unit,
)

@Composable
expect fun rememberCameraAccess(): CameraAccess

/** Proporzioni di una carta Pokemon (63 x 88 mm). */
internal const val CARD_ASPECT_RATIO = 63f / 88f

/** Larghezza della cornice rispetto allo schermo. */
internal const val SCAN_ZONE_WIDTH_FRACTION = 0.88f

/** Spostamento verso l'alto della cornice, per lasciare spazio ai pannelli in basso. */
internal const val SCAN_ZONE_VERTICAL_OFFSET = 0.05f

/** Altezza massima della cornice rispetto allo schermo (orizzontale). */
internal const val SCAN_ZONE_MAX_HEIGHT_FRACTION = 0.80f

/**
 * Quota da cui parte la striscia con il numero, in frazione dell'altezza
 * della carta: generosa, nessuno allinea la carta alla cornice al pixel.
 */
internal const val ID_STRIP_TOP = 0.78f

/**
 * La cornice di scansione nelle coordinate di chi la riceve: la usano sia
 * l'overlay (sullo schermo) sia la fotocamera (sull'immagine visibile), cosi'
 * cio' che l'utente inquadra e' cio' che l'OCR legge davvero.
 */
internal fun scanZoneRect(width: Float, height: Float): Rect {
    var zoneWidth = width * SCAN_ZONE_WIDTH_FRACTION
    var zoneHeight = zoneWidth / CARD_ASPECT_RATIO

    val maxHeight = height * SCAN_ZONE_MAX_HEIGHT_FRACTION
    if (zoneHeight > maxHeight) {
        zoneHeight = maxHeight
        zoneWidth = zoneHeight * CARD_ASPECT_RATIO
    }

    val left = (width - zoneWidth) / 2f
    val top = (height - zoneHeight) / 2f - height * SCAN_ZONE_VERTICAL_OFFSET
    return Rect(left, top, left + zoneWidth, top + zoneHeight)
}
