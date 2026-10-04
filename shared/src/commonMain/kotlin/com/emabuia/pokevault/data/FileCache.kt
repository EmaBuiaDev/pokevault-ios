package com.emabuia.pokevault.data

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Quello che finisce su disco: il dato e quando e' stato scaricato. */
@Serializable
data class Cached<T>(val savedAt: Long, val data: T)

/**
 * Cache su file, un JSON per chiave, nella cartella che la piattaforma da'
 * per le cache (Caches su iOS, cacheDir su Android).
 *
 * File e non preferenze: su Android lo snapshot prezzi stava nelle
 * SharedPreferences e veniva riletto per intero a ogni avvio; qui ogni
 * espansione ha il suo file e si legge solo quella che si apre.
 *
 * Nessun errore esce da qui: una cache illeggibile e' una cache vuota, e
 * l'app ripiega sulla rete.
 */
class FileCache(
    private val directory: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
    @OptIn(ExperimentalTime::class)
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    fun <T> read(key: String, serializer: KSerializer<T>): Cached<T>? = runCatching {
        val path = pathOf(key)
        if (!SystemFileSystem.exists(path)) return null
        val text = SystemFileSystem.source(path).buffered().use { it.readString() }
        json.decodeFromString(Cached.serializer(serializer), text)
    }.getOrNull()

    fun <T> write(key: String, serializer: KSerializer<T>, data: T) {
        runCatching {
            SystemFileSystem.createDirectories(Path(directory))
            // Prima un file temporaneo, poi lo spostamento: un'app chiusa a meta'
            // scrittura lascia il file vecchio intero, non uno troncato.
            val tmp = Path(directory, "$key.tmp")
            SystemFileSystem.sink(tmp).buffered().use {
                it.writeString(json.encodeToString(Cached.serializer(serializer), Cached(now(), data)))
            }
            SystemFileSystem.atomicMove(tmp, pathOf(key))
        }
    }

    fun ageMillis(cached: Cached<*>): Long = now() - cached.savedAt

    private fun pathOf(key: String) = Path(directory, "${key.filter { it.isLetterOrDigit() || it == '_' || it == '-' }}.json")
}
