package com.emabuia.pokevault.data

import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.utils.io.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Se l'account e' Premium, come lo decide PremiumManager su Android quando il
 * telefono non ha un acquisto suo: l'entitlement che il Worker ha legato
 * all'account (comprato su Android), oppure un regalo non ancora scaduto.
 *
 * Solo letture: su iOS non si compra niente finche' non arriva l'acquisto Apple.
 */
class PremiumRepository(
    private val auth: AuthRepository,
    private val client: HttpClient,
    private val now: () -> Long,
    private val baseUrl: String = WORKER_BASE_URL,
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Entitlement(val entitled: Boolean? = null)

    @Serializable
    private data class GiftStatus(val giftUntilMs: Long? = null)

    /** null se il server non risponde: chi non sa non toglie niente a nessuno. */
    suspend fun isPremium(): Boolean? {
        val token = auth.validIdToken() ?: return false
        val base = baseUrl.trimEnd('/')
        val bought = read { json.decodeFromString<Entitlement>(client.get("$base/v1/billing/entitlement") { bearerAuth(token) }.bodyAsText()).entitled == true }
        if (bought == true) return true
        val gift = read { (json.decodeFromString<GiftStatus>(client.get("$base/v1/gift/me") { bearerAuth(token) }.bodyAsText()).giftUntilMs ?: 0L) > now() }
        if (gift == true) return true
        return if (bought == null || gift == null) null else false
    }

    private suspend fun read(block: suspend () -> Boolean): Boolean? = try {
        block()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        null
    }

    companion object {
        /** PremiumManager.FREE_WISHLIST_LIMIT su Android. */
        const val FREE_WISHLIST_LIMIT = 1
        const val FREE_ALBUM_LIMIT = 1
        const val FREE_GOAL_ALBUM_LIMIT = 1
    }
}
