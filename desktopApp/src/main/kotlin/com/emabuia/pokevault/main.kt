package com.emabuia.pokevault

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.emabuia.pokevault.di.initKoin

fun main() {
    initKoin()

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "PokeVault",
        ) {
            App()
        }
    }
}
