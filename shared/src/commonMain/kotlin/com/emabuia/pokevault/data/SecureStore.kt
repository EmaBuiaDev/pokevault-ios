package com.emabuia.pokevault.data

import kotlinx.serialization.builtins.serializer

/**
 * Dove stanno i segreti (la sessione col refresh token): il Portachiavi su
 * iOS, come fa l'SDK Firebase. Come [FileCache], nessun errore esce da qui:
 * un segreto illeggibile e' un segreto che non c'e', e si rifa' il login.
 */
interface SecureStore {
    fun read(key: String): String?
    fun write(key: String, value: String)
    fun remove(key: String)
}

/** Il Portachiavi su iOS; un file nei dati dell'app su Android e desktop, che servono solo a provare. */
expect fun platformSecureStore(data: FileCache): SecureStore

/** Un file per chiave in [cache]: per Android, desktop e test. */
class FileSecureStore(private val cache: FileCache) : SecureStore {
    override fun read(key: String): String? = cache.read(key, String.serializer())?.data
    override fun write(key: String, value: String) = cache.write(key, String.serializer(), value)
    override fun remove(key: String) = cache.remove(key)
}
