package com.emabuia.pokevault.util

import com.emabuia.pokevault.data.WORKER_BASE_URL

/**
 * ImageUrlUtils dell'app Android, senza android.net.Uri.
 *
 * Le carte salvate in collezione possono avere l'immagine su
 * api.pokewallet.io: va fatta passare dal Worker, che la mette in cache e
 * tiene la chiave PokeWallet lato server. Chiamare PokeWallet diretto
 * consumerebbe il suo limite orario, condiviso da tutti gli utenti.
 */
object ImageUrlUtils {

    /** Spazi e parentesi rompono l'URL dell'immagine (alcuni nomi PokeWallet li hanno). */
    fun safeImageUrl(url: String): String {
        return url
            .replace(" ", "%20")
            .replace("(", "%28")
            .replace(")", "%29")
    }

    fun proxyPokeWalletUrl(url: String, proxyBase: String = WORKER_BASE_URL): String {
        if (url.isBlank()) return url
        val afterScheme = url.substringAfter("://", missingDelimiterValue = "")
        val host = afterScheme.substringBefore('/').substringBefore('?').lowercase()
        if (host != "api.pokewallet.io") return url
        val pathAndQuery = afterScheme.removePrefix(afterScheme.substringBefore('/')).trimStart('/')
        return "${proxyBase.trimEnd('/')}/$pathAndQuery"
    }

    fun safeProxiedImageUrl(url: String): String = safeImageUrl(proxyPokeWalletUrl(url))
}
