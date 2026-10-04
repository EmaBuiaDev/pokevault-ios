package com.emabuia.pokevault

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.emabuia.pokevault.di.initKoin

fun main() {
    val home = java.io.File(System.getProperty("user.home"), ".pokevault-kmp")
    initKoin(cacheDir = java.io.File(home, "cache").absolutePath, dataDir = java.io.File(home, "data").absolutePath)

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "PokeVault",
        ) {
            App()
        }
    }
}
