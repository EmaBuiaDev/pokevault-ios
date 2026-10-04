package com.emabuia.pokevault.di

import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * Chiamata da iOSApp.swift all'avvio.
 * Cache in Library/Caches, che iOS puo' svuotare se serve spazio; la sessione
 * in Library/Application Support, che resta e finisce nel backup.
 */
fun initKoin() {
    initKoin(
        cacheDir = "${directory(NSCachesDirectory)}/catalog",
        dataDir = "${directory(NSApplicationSupportDirectory)}/data",
    )
}

private fun directory(kind: NSSearchPathDirectory): String =
    NSSearchPathForDirectoriesInDomains(kind, NSUserDomainMask, true).first() as String
