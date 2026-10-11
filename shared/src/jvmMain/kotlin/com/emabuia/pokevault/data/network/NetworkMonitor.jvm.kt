package com.emabuia.pokevault.data.network

/** Solo per lo sviluppo: qui l'app resta sempre "online". */
internal actual fun watchPlatformNetwork(onChange: (Boolean) -> Unit) = Unit
