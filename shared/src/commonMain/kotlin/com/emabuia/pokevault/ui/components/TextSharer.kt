package com.emabuia.pokevault.ui.components

import androidx.compose.runtime.Composable

/**
 * Copiare un testo o passarlo a un'altra app: ClipboardManager e
 * Intent.ACTION_SEND su Android, UIPasteboard e il foglio di condivisione su iOS.
 */
interface TextSharer {
    fun copy(text: String)
    fun share(subject: String, text: String)
}

@Composable
expect fun rememberTextSharer(): TextSharer
