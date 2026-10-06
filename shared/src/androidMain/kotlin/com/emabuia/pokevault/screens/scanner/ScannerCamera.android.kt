package com.emabuia.pokevault.screens.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.emabuia.pokevault.ocr.ScannedFrame

/**
 * L'app Android di questo repo serve solo a provare il codice comune: lo
 * Scanner vero su Android e' quello dell'app PokeVault di Play.
 */
@Composable
actual fun ScannerCameraPreview(
    onFrameScanned: (ScannedFrame) -> Unit,
    flashEnabled: Boolean,
    scanEnabled: Boolean,
    modifier: Modifier,
) {
    Box(modifier.fillMaxSize().background(Color(0xFF202030)), contentAlignment = Alignment.Center) {
        Text("Fotocamera disponibile solo su iPhone", color = Color.White)
    }
}

@Composable
actual fun rememberCameraAccess(): CameraAccess = CameraAccess(granted = true, deniedForever = false, request = {})
