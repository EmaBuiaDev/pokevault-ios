package com.emabuia.pokevault.di

import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/** Chiamata da iOSApp.swift all'avvio: Library/Caches dell'app, che iOS puo' svuotare se serve spazio. */
fun initKoin() {
    val caches = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String
    initKoin("$caches/catalog")
}
