package com.emabuia.pokevault

import android.app.Application
import com.emabuia.pokevault.di.initKoin

class PokeVaultApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(java.io.File(cacheDir, "catalog").absolutePath)
    }
}
