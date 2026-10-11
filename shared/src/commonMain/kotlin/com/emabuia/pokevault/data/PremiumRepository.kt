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
    /** Dati dell'app: l'ultimo esito certo, per quando il server non risponde. */
    private val store: FileCache? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Entitlement(val entitled: Boolean? = null)

    @Serializable
    private data class GiftStatus(val giftUntilMs: Long? = null)

    /**
     * L'ultimo esito certo, legato all'account (SubscriptionMemory su Android):
     * l'abbonamento vale ancora per [SubscriptionMemory.GRACE_MS], il regalo fino
     * alla sua scadenza, come la copia su disco di Android.
     */
    @Serializable
    private data class Remembered(
        val uid: String,
        val entitled: Boolean? = null,
        val confirmedAtMs: Long = 0L,
        val giftUntilMs: Long? = null,
    )

    /**
     * null se il server non risponde e sul telefono non c'e' un esito recente:
     * chi non sa non toglie niente a nessuno. Senza rete vale l'ultimo esito
     * certo dello stesso account, come su Android: chi ha pagato non torna
     * gratuito appena perde la rete.
     */
    suspend fun isPremium(): Boolean? {
        val token = auth.validIdToken() ?: return false
        val uid = auth.session.value?.uid.orEmpty()
        val base = baseUrl.trimEnd('/')
        val bought = read { json.decodeFromString<Entitlement>(client.get("$base/v1/billing/entitlement") { bearerAuth(token) }.bodyAsText()).entitled == true }
        val giftUntil = readLong { json.decodeFromString<GiftStatus>(client.get("$base/v1/gift/me") { bearerAuth(token) }.bodyAsText()).giftUntilMs ?: 0L }
        remember(uid, bought, giftUntil)

        val stored = recalled(uid)
        val subscribed: Boolean? = bought ?: run {
            val memory = stored?.entitled?.let { RememberedSubscription(stored.uid, it, stored.confirmedAtMs) }
            if (SubscriptionMemory.isStillValid(memory, uid, now())) true else null
        }
        val gift: Boolean? = (giftUntil ?: stored?.giftUntilMs)?.let { it > now() }
        if (subscribed == true || gift == true) return true
        return if (subscribed == null || gift == null) null else false
    }

    /** Ogni esito certo si salva per l'account corrente, con l'ora. */
    private fun remember(uid: String, bought: Boolean?, giftUntil: Long?) {
        val store = store ?: return
        if (uid.isBlank() || (bought == null && giftUntil == null)) return
        val previous = recalled(uid)
        val next = Remembered(
            uid = uid,
            entitled = bought ?: previous?.entitled,
            confirmedAtMs = if (bought != null) now() else previous?.confirmedAtMs ?: 0L,
            giftUntilMs = giftUntil ?: previous?.giftUntilMs,
        )
        store.write(MEMORY_KEY, Remembered.serializer(), next)
    }

    private fun recalled(uid: String): Remembered? =
        store?.read(MEMORY_KEY, Remembered.serializer())?.data?.takeIf { it.uid == uid && uid.isNotBlank() }

    private suspend fun readLong(block: suspend () -> Long): Long? = try {
        block()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        null
    }

    /**
     * Fino a quando dura il mese regalo (PremiumManager.giftUntilMs su
     * Android); 0 se non c'e' o se il server non risponde.
     */
    suspend fun giftUntilMs(): Long {
        val token = auth.validIdToken() ?: return 0L
        return try {
            json.decodeFromString<GiftStatus>(
                client.get("${baseUrl.trimEnd('/')}/v1/gift/me") { bearerAuth(token) }.bodyAsText()
            ).giftUntilMs ?: 0L
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            0L
        }
    }

    private suspend fun read(block: suspend () -> Boolean): Boolean? = try {
        block()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        null
    }

    companion object {
        private const val MEMORY_KEY = "premium_memory"

        /** PremiumManager.FREE_WISHLIST_LIMIT su Android. */
        const val FREE_WISHLIST_LIMIT = 1
        const val FREE_ALBUM_LIMIT = 1
        const val FREE_GOAL_ALBUM_LIMIT = 1
        const val FREE_TOURNAMENT_LIMIT = 1
        const val FREE_DECK_LIMIT = 1
    }
}
