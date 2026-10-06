package com.emabuia.pokevault.data.trade

import com.emabuia.pokevault.data.AuthRepository
import com.emabuia.pokevault.data.WORKER_BASE_URL
import com.emabuia.pokevault.data.trade.dto.TradeAddSpotPayload
import com.emabuia.pokevault.data.trade.dto.TradeAddSpotRequest
import com.emabuia.pokevault.data.trade.dto.TradeAvatarRequest
import com.emabuia.pokevault.data.trade.dto.TradeBlocksPayload
import com.emabuia.pokevault.data.trade.dto.TradeCellUpload
import com.emabuia.pokevault.data.trade.dto.TradeConfirmRequest
import com.emabuia.pokevault.data.trade.dto.TradeCounterRequest
import com.emabuia.pokevault.data.trade.dto.TradeCreatedPayload
import com.emabuia.pokevault.data.trade.dto.TradeErrorPayload
import com.emabuia.pokevault.data.trade.dto.TradeHavesPayload
import com.emabuia.pokevault.data.trade.dto.TradeLeaderboardPayload
import com.emabuia.pokevault.data.trade.dto.TradeMatchesPayload
import com.emabuia.pokevault.data.trade.dto.TradeMeetingRequest
import com.emabuia.pokevault.data.trade.dto.TradeMePayload
import com.emabuia.pokevault.data.trade.dto.TradeNotifyPrefs
import com.emabuia.pokevault.data.trade.dto.TradeOptInRequest
import com.emabuia.pokevault.data.trade.dto.TradeOwnedRequest
import com.emabuia.pokevault.data.trade.dto.TradeProfilePayload
import com.emabuia.pokevault.data.trade.dto.TradeProfileRequest
import com.emabuia.pokevault.data.trade.dto.TradeProposalRequest
import com.emabuia.pokevault.data.trade.dto.TradeProposalsPayload
import com.emabuia.pokevault.data.trade.dto.TradeRateRequest
import com.emabuia.pokevault.data.trade.dto.TradeReportRequest
import com.emabuia.pokevault.data.trade.dto.TradeSpotSearchPayload
import com.emabuia.pokevault.data.trade.dto.TradeSpotVoteRequest
import com.emabuia.pokevault.data.trade.dto.TradeSpotsPayload
import com.emabuia.pokevault.data.trade.dto.TradeUserHavesPayload
import com.emabuia.pokevault.data.trade.dto.TradeWantsRequest
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import io.ktor.utils.io.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Le chiamate a TradeRadar: data/trade/TradeApi.kt di Android, sugli stessi
 * endpoint (pokevault-proxy-worker/src/trade.ts) e con le stesse risposte.
 *
 * In produzione il server e' il Worker del catalogo, come per il flavor prod
 * di Android: un utente vede gli stessi scambi da tutti e due i telefoni.
 */
class TradeApi(
    private val client: HttpClient,
    private val auth: AuthRepository,
    private val baseUrl: String = WORKER_BASE_URL,
) {
    /**
     * Come Gson su Android: i null non si scrivono (il server li tratterebbe
     * come valori), i valori di default si'.
     */
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true; coerceInputValues = true }

    /** Esito di una chiamata. */
    sealed class Result<out T> {
        data class Ok<T>(val value: T) : Result<T>()

        /** 404 "no_profile": l'utente non ha ancora attivato TradeRadar. */
        data object NoProfile : Result<Nothing>()

        /** 401: token assente o di un altro progetto Firebase. */
        data object Unauthorized : Result<Nothing>()

        /** Il server ha rifiutato i dati (400/403/409/413/429), con la sigla d'errore. */
        data class Rejected(val error: String?) : Result<Nothing>()

        /** Rete, 5xx, risposta illeggibile. */
        data class Unavailable(val httpCode: Int?) : Result<Nothing>()
    }

    private suspend fun <T> call(method: HttpMethod, path: String, body: JsonElement?, parse: (String) -> T?): Result<T> {
        val token = auth.validIdToken() ?: return Result.Unauthorized
        return try {
            val response = client.request("${baseUrl.trimEnd('/')}/$path") {
                this.method = method
                expectSuccess = false
                bearerAuth(token)
                // Il server vuole un corpo su POST e PUT anche senza dati.
                val payload = body ?: if (method == HttpMethod.Post || method == HttpMethod.Put) JsonObject(emptyMap()) else null
                if (payload != null) {
                    contentType(ContentType.Application.Json)
                    setBody(payload.toString())
                }
            }
            val text = response.bodyAsText()
            val code = response.status.value
            val error = runCatching { json.decodeFromString(TradeErrorPayload.serializer(), text).error }.getOrNull()
            when {
                code == 401 -> Result.Unauthorized
                code == 404 && error == "no_profile" -> Result.NoProfile
                // 409: proposta gia' aperta, carte non piu' offerte, non e' il tuo turno.
                // 403: profilo sospeso.
                code == 400 || code == 403 || code == 409 || code == 413 || code == 429 -> Result.Rejected(error)
                !response.status.isSuccess() -> Result.Unavailable(code)
                else -> runCatching { parse(text) }.getOrNull()?.let { Result.Ok(it) } ?: Result.Unavailable(code)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.Unavailable(null)
        }
    }

    private fun <T> parser(serializer: KSerializer<T>): (String) -> T? = { text -> json.decodeFromString(serializer, text) }
    private fun <T> body(serializer: KSerializer<T>, value: T): JsonElement = json.encodeToJsonElement(serializer, value)
    private val unit: (String) -> Unit = { }
    private val empty: JsonElement = JsonObject(emptyMap())

    /** GET /v1/trade/me: il server mi raggiunge e sa chi sono? */
    suspend fun me(): Result<TradeMePayload> = call(HttpMethod.Get, "v1/trade/me", null, parser(TradeMePayload.serializer()))

    suspend fun getProfile(): Result<TradeProfilePayload> =
        call(HttpMethod.Get, "v1/trade/profile", null, parser(TradeProfilePayload.serializer()))

    suspend fun putProfile(request: TradeProfileRequest): Result<TradeProfilePayload> =
        call(HttpMethod.Put, "v1/trade/profile", body(TradeProfileRequest.serializer(), request), parser(TradeProfilePayload.serializer()))

    /** Disattivazione: il server cancella profilo, liste e possedute. */
    suspend fun deleteProfile(): Result<Unit> = call(HttpMethod.Delete, "v1/trade/profile", null, unit)

    suspend fun getHaves(): Result<TradeHavesPayload> = call(HttpMethod.Get, "v1/trade/haves", null, parser(TradeHavesPayload.serializer()))

    suspend fun putHaves(payload: TradeHavesPayload): Result<Unit> =
        call(HttpMethod.Put, "v1/trade/haves", body(TradeHavesPayload.serializer(), payload), unit)

    suspend fun putWants(request: TradeWantsRequest): Result<Unit> =
        call(HttpMethod.Put, "v1/trade/wants", body(TradeWantsRequest.serializer(), request), unit)

    suspend fun putOwned(request: TradeOwnedRequest): Result<Unit> =
        call(HttpMethod.Put, "v1/trade/owned", body(TradeOwnedRequest.serializer(), request), unit)

    suspend fun matches(): Result<TradeMatchesPayload> = call(HttpMethod.Get, "v1/trade/matches", null, parser(TradeMatchesPayload.serializer()))

    // ── Proposte ────────────────────────────────────────────────────────────

    /** Le carte che offre un'altra persona, per comporre una proposta. */
    suspend fun userHaves(publicId: String): Result<TradeUserHavesPayload> =
        call(HttpMethod.Get, "v1/trade/users/$publicId/haves", null, parser(TradeUserHavesPayload.serializer()))

    suspend fun proposals(): Result<TradeProposalsPayload> =
        call(HttpMethod.Get, "v1/trade/proposals", null, parser(TradeProposalsPayload.serializer()))

    suspend fun createProposal(request: TradeProposalRequest): Result<TradeCreatedPayload> =
        call(HttpMethod.Post, "v1/trade/proposals", body(TradeProposalRequest.serializer(), request), parser(TradeCreatedPayload.serializer()))

    /** accept | decline | cancel; per counter c'e' [counterProposal]. */
    suspend fun actOnProposal(id: String, action: String): Result<Unit> =
        call(HttpMethod.Post, "v1/trade/proposals/$id/$action", empty, unit)

    suspend fun counterProposal(id: String, request: TradeCounterRequest): Result<Unit> =
        call(HttpMethod.Post, "v1/trade/proposals/$id/counter", body(TradeCounterRequest.serializer(), request), unit)

    // ── Luoghi e appuntamento ───────────────────────────────────────────────

    suspend fun proposalSpots(id: String): Result<TradeSpotsPayload> =
        call(HttpMethod.Get, "v1/trade/proposals/$id/spots", null, parser(TradeSpotsPayload.serializer()))

    /** I dati grezzi di Overpass per una zona: il filtro lo fa il server. */
    suspend fun uploadCell(upload: TradeCellUpload): Result<Unit> =
        call(HttpMethod.Post, "v1/trade/spots/cell", body(TradeCellUpload.serializer(), upload), unit)

    /** "Manca un negozio?": ricerca su OpenStreetMap vicino al punto a meta' strada. */
    suspend fun searchSpots(query: String, proposalId: String?): Result<TradeSpotSearchPayload> {
        val near = proposalId?.let { "&proposal=$it" }.orEmpty()
        return call(HttpMethod.Get, "v1/trade/spots/search?q=${query.encodeURLParameter()}$near", null, parser(TradeSpotSearchPayload.serializer()))
    }

    suspend fun addSpot(request: TradeAddSpotRequest): Result<TradeAddSpotPayload> =
        call(HttpMethod.Post, "v1/trade/spots", body(TradeAddSpotRequest.serializer(), request), parser(TradeAddSpotPayload.serializer()))

    suspend fun proposeMeeting(id: String, request: TradeMeetingRequest): Result<Unit> =
        call(HttpMethod.Post, "v1/trade/proposals/$id/meeting", body(TradeMeetingRequest.serializer(), request), unit)

    suspend fun confirmMeeting(id: String, slotIndex: Int): Result<Unit> =
        call(HttpMethod.Post, "v1/trade/proposals/$id/meeting/confirm", body(TradeConfirmRequest.serializer(), TradeConfirmRequest(slotIndex)), unit)

    // ── Chiusura e feedback ─────────────────────────────────────────────────

    /** "Scambio fatto": dal giorno dell'appuntamento; con entrambi lo scambio e' chiuso. */
    suspend fun markDone(id: String): Result<Unit> = call(HttpMethod.Post, "v1/trade/proposals/$id/done", empty, unit)

    /** "Non si e' presentato": dopo l'ora dell'appuntamento. */
    suspend fun markNoShow(id: String): Result<Unit> = call(HttpMethod.Post, "v1/trade/proposals/$id/noshow", empty, unit)

    /** "Io c'ero": chi e' stato segnalato come assente, entro 48 ore. */
    suspend fun disputeNoShow(id: String): Result<Unit> = call(HttpMethod.Post, "v1/trade/proposals/$id/dispute", empty, unit)

    suspend fun rate(id: String, request: TradeRateRequest): Result<Unit> =
        call(HttpMethod.Post, "v1/trade/proposals/$id/rate", body(TradeRateRequest.serializer(), request), unit)

    suspend fun voteSpot(id: String, request: TradeSpotVoteRequest): Result<Unit> =
        call(HttpMethod.Post, "v1/trade/proposals/$id/spotvote", body(TradeSpotVoteRequest.serializer(), request), unit)

    // ── Classifica ──────────────────────────────────────────────────────────

    /** [scope]: "zone" (le celle dei match) o "italy". */
    suspend fun leaderboard(scope: String): Result<TradeLeaderboardPayload> =
        call(HttpMethod.Get, "v1/trade/leaderboard?scope=$scope", null, parser(TradeLeaderboardPayload.serializer()))

    suspend fun setLeaderboardOptIn(optIn: Boolean): Result<Unit> =
        call(HttpMethod.Put, "v1/trade/leaderboard/optin", body(TradeOptInRequest.serializer(), TradeOptInRequest(optIn)), unit)

    // ── Segnala e blocca ────────────────────────────────────────────────────

    suspend fun block(publicId: String): Result<Unit> = call(HttpMethod.Post, "v1/trade/users/$publicId/block", null, unit)

    suspend fun unblock(publicId: String): Result<Unit> = call(HttpMethod.Delete, "v1/trade/users/$publicId/block", null, unit)

    suspend fun blocks(): Result<TradeBlocksPayload> = call(HttpMethod.Get, "v1/trade/blocks", null, parser(TradeBlocksPayload.serializer()))

    suspend fun report(publicId: String, request: TradeReportRequest): Result<Unit> =
        call(HttpMethod.Post, "v1/trade/users/$publicId/report", body(TradeReportRequest.serializer(), request), unit)

    // ── Notifiche ───────────────────────────────────────────────────────────

    /**
     * Le preferenze delle notifiche: solo i campi presenti cambiano. Su iOS le
     * notifiche push arriveranno con l'account sviluppatore Apple (APNs);
     * intanto le preferenze valgono per gli altri telefoni dell'utente.
     */
    suspend fun setNotifyPrefs(changes: Map<String, Boolean>): Result<TradeNotifyPrefs> =
        call(HttpMethod.Put, "v1/trade/notify", body(MapSerializer(String.serializer(), Boolean.serializer()), changes), parser(TradeNotifyPrefs.serializer()))

    /** Il Pokemon del podio; [avatar] null torna all'iniziale. */
    suspend fun setAvatar(avatar: Int?, animated: Boolean): Result<Unit> =
        call(HttpMethod.Put, "v1/trade/avatar", body(TradeAvatarRequest.serializer(), TradeAvatarRequest(avatar, animated)), unit)
}
