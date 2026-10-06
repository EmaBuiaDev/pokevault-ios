package com.emabuia.pokevault.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.math.max
import kotlin.math.min
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

// ── Modelli della risposta di LimitlessTCG (LimitlessTcgApiService.kt su Android) ──

@Serializable
data class LimitlessTournament(
    val id: String = "",
    val game: String = "",
    val format: String = "",
    val name: String = "",
    val date: String = "",
    val players: Int = 0
)

/**
 * Un giocatore nei risultati di un torneo. La decklist arriva in forme
 * diverse (mappa per categoria o lista piatta): resta JSON e la legge
 * [LimitlessDecklists].
 */
@Serializable
data class LimitlessStanding(
    val player: String = "",
    val name: String = "",
    val placing: Int? = null,
    val record: LimitlessRecord? = null,
    val decklist: JsonElement? = null,
    val deck: LimitlessDeckInfo? = null,
    val country: String? = null
) {
    /** Come il DTO di Android, dove un null diventava 0: vedi [LimitlessPlacing]. */
    val placingOrZero: Int get() = placing ?: 0
}

@Serializable
data class LimitlessRecord(
    val wins: Int = 0,
    val losses: Int = 0,
    val ties: Int = 0
)

@Serializable
data class LimitlessDeckInfo(
    val name: String? = null,
    val icons: JsonElement? = null
)

@Serializable
data class LimitlessOrganizer(
    val id: Int = 0,
    val name: String = "",
    val logo: String? = null
)

/** Il dettaglio di un torneo: serve per [isOnline], che la lista non dice. */
@Serializable
data class LimitlessTournamentDetails(
    val id: String = "",
    val game: String = "",
    val format: String = "",
    val name: String = "",
    val date: String = "",
    val players: Int = 0,
    val organizer: LimitlessOrganizer? = null,
    /** false quando il torneo si e' giocato di persona. */
    val isOnline: Boolean? = null,
    val isPublic: Boolean? = null,
    val decklists: Boolean? = null
)

/** La finestra di Limitless e' chiusa: non e' un guasto, si aspetta. */
class LimitlessRateLimitException(
    val retryAfterSeconds: Long
) : Exception("Limitless rate limit: riprovare fra ${retryAfterSeconds}s")

/**
 * Il limite dell'API Limitless (50 richieste ogni 5 minuti) contato qui,
 * prima di mandare la richiesta: LimitlessRateLimiter.kt di Android, con le
 * stesse soglie e la stessa riserva. Senza sincronizzazione: lo usa solo
 * [LimitlessApi], che gira su un thread alla volta.
 */
@OptIn(ExperimentalTime::class)
internal class LimitlessRateLimiter(
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val recent = ArrayDeque<Long>()
    private var serverRemaining: Int? = null
    private var serverResetAtMs: Long = 0L
    private var blockedUntilMs: Long = 0L

    fun tryAcquire(): Boolean {
        val now = now()
        if (now < blockedUntilMs) return false
        prune(now)
        if (remaining(now) <= RESERVE) return false
        recent.addLast(now)
        return true
    }

    /** L'header "RateLimit" di Limitless: r = richieste rimaste, t = secondi al reset. */
    fun onHeaders(rateLimitHeader: String?) {
        if (rateLimitHeader.isNullOrBlank()) return
        val remaining = REMAINING_REGEX.find(rateLimitHeader)?.groupValues?.getOrNull(1)?.toIntOrNull()
        val resetIn = RESET_REGEX.find(rateLimitHeader)?.groupValues?.getOrNull(1)?.toLongOrNull()
        val now = now()
        if (remaining != null) serverRemaining = remaining
        if (resetIn != null) serverResetAtMs = now + resetIn * 1000L
    }

    fun onTooManyRequests(retryAfterSeconds: Long?) {
        val now = now()
        val waitMs = (retryAfterSeconds?.times(1000L))
            ?: (serverResetAtMs - now).takeIf { it > 0 }
            ?: WINDOW_MS
        blockedUntilMs = max(blockedUntilMs, now + waitMs)
        serverRemaining = 0
    }

    fun retryAfterSeconds(): Long {
        val now = now()
        if (now < blockedUntilMs) return ((blockedUntilMs - now) / 1000L) + 1
        prune(now)
        if (remaining(now) > RESERVE) return 0
        val oldest = recent.firstOrNull() ?: return 0
        return (((oldest + WINDOW_MS) - now) / 1000L).coerceAtLeast(1L)
    }

    private fun prune(now: Long) {
        while (recent.isNotEmpty() && now - recent.first() >= WINDOW_MS) recent.removeFirst()
    }

    private fun remaining(now: Long): Int {
        val local = QUOTA - recent.size
        val server = serverRemaining?.takeIf { now < serverResetAtMs } ?: QUOTA
        return min(local, server)
    }

    private companion object {
        const val WINDOW_MS = 5 * 60 * 1000L
        const val QUOTA = 50
        /** Margine per non arrivare mai all'ultima richiesta della finestra. */
        const val RESERVE = 6
        val REMAINING_REGEX = Regex("""\br=(\d+)""")
        val RESET_REGEX = Regex("""\bt=(\d+)""")
    }
}

/** Le tre chiamate di LimitlessTcgApiService, con il limitatore davanti. */
internal class LimitlessApi(
    private val client: HttpClient,
    private val limiter: LimitlessRateLimiter,
    private val baseUrl: String = "https://play.limitlesstcg.com/api",
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    suspend fun getTournaments(game: String = "PTCG", format: String = "standard", limit: Int = 10): List<LimitlessTournament> =
        json.decodeFromString(get("$baseUrl/tournaments?game=$game&format=$format&limit=$limit&page=1"))

    suspend fun getTournamentDetails(tournamentId: String): LimitlessTournamentDetails =
        json.decodeFromString(get("$baseUrl/tournaments/$tournamentId/details"))

    suspend fun getTournamentStandings(tournamentId: String): List<LimitlessStanding> =
        json.decodeFromString(get("$baseUrl/tournaments/$tournamentId/standings"))

    fun retryAfterSeconds(): Long = limiter.retryAfterSeconds()

    private suspend fun get(url: String): String {
        if (!limiter.tryAcquire()) throw LimitlessRateLimitException(limiter.retryAfterSeconds())
        val response: HttpResponse = client.get(url) { expectSuccess = false }
        limiter.onHeaders(response.headers["RateLimit"])
        if (response.status == HttpStatusCode.TooManyRequests) {
            limiter.onTooManyRequests(response.headers["Retry-After"]?.toLongOrNull())
            throw LimitlessRateLimitException(limiter.retryAfterSeconds())
        }
        val text = response.bodyAsText()
        check(response.status.isSuccess()) { "Limitless ${response.status.value}" }
        return text
    }
}

/**
 * I piazzamenti di Limitless, con quelli che mancano messi al loro posto:
 * LimitlessPlacing.kt di Android. Un ritirato arriva senza piazzamento (0).
 */
internal object LimitlessPlacing {
    fun isKnown(placing: Int): Boolean = placing > 0

    fun <T> ranked(items: List<T>, placing: (T) -> Int): List<T> =
        items.sortedWith(
            compareBy<T> { if (isKnown(placing(it))) 0 else 1 }
                .thenBy { placing(it) }
        )

    fun best(placings: List<Int>): Int = placings.filter(::isKnown).minOrNull() ?: 0
}
