package com.emabuia.pokevault.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/** Sul desktop, che serve solo a provare, condividere vuol dire copiare. */
@Composable
actual fun rememberTextSharer(): TextSharer = remember {
    object : TextSharer {
        override fun copy(text: String) {
            runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
        }

        override fun share(subject: String, text: String) = copy(text)
    }
}
