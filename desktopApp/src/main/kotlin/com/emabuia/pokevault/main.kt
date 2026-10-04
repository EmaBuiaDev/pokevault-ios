package com.emabuia.pokevault

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.emabuia.pokevault.di.initKoin

fun main() {
    initKoin(java.io.File(System.getProperty("user.home"), ".pokevault-kmp/cache").absolutePath)

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "PokeVault",
        ) {
            App()
        }
    }
}
