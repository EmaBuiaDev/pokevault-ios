package com.emabuia.pokevault.data.trade.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Clock

/*
 * Richieste e risposte del Worker di TradeRadar, lette e scritte con Gson.
 *
 * Stanno in un package a parte perche' proguard-rules.pro tiene per intero
 * questo e nient'altro di data.trade: senza la regola R8 le considera sempre
 * null (le scrive solo Gson, per reflection) e in release ogni risposta
 * sembrerebbe vuota. Tenere l'intero data.trade invece impedirebbe a R8 di
 * togliere TradeRadar dalla build prod, dove e' spento.
 *
 * I nomi JSON sono fissati da @SerializedName: rinominare una proprieta'
 * Kotlin non deve rompere il contratto con src/trade.ts.
 */

/** GET /v1/trade/me */
@Serializable
data class TradeMePayload(
    @SerialName("uid") val uid: String? = null,
    @SerialName("schemaVersion") val schemaVersion: Int? = null
)

/** PUT /v1/trade/profile */
@Serializable
data class TradeProfileRequest(
    @SerialName("nickname") val nickname: String,
    @SerialName("geohash5") val geohash5: String,
    @SerialName("adultConfirmed") val adultConfirmed: Boolean,
    @SerialName("collectionConsent") val collectionConsent: Boolean,
    @SerialName("paused") val paused: Boolean
)

/** GET e PUT /v1/trade/profile */
@Serializable
data class TradeProfilePayload(
    @SerialName("nickname") val nickname: String? = null,
    @SerialName("geohash5") val geohash5: String? = null,
    @SerialName("paused") val paused: Boolean? = null,
    @SerialName("ownedHash") val ownedHash: String? = null,
    @SerialName("tradesDone") val tradesDone: Int? = null,
    @SerialName("memberSince") val memberSince: Long? = null,
    @SerialName("haves") val haves: Int? = null,
    @SerialName("wants") val wants: Int? = null,
    @SerialName("owned") val owned: Int? = null,
    /** I miei voti ricevuti (fase 2c). */
    @SerialName("reputation") val reputation: TradeReputation? = null,
    /** Il mio livello e se compaio in classifica (null = mai chiesto) (fase 2e). */
    @SerialName("tier") val tier: String? = null,
    @SerialName("leaderboardOptIn") val leaderboardOptIn: Boolean? = null,
    /** Il Pokemon del podio (numero di Pokedex), null = l'iniziale. */
    @SerialName("avatar") val avatar: Int? = null,
    @SerialName("avatarAnimated") val avatarAnimated: Boolean? = null,
    /** Sospeso fino a (ms), e perche': no_show | reports | admin. Null se non lo e' (fase 2f). */
    @SerialName("suspendedUntil") val suspendedUntil: Long? = null,
    @SerialName("suspensionReason") val suspensionReason: String? = null,
    /** Le categorie di notifiche (fase 3). */
    @SerialName("notify") val notify: TradeNotifyPrefs? = null,
    /** Prova gratuita / Premium / solo ricevere (schema 13). Null da un server piu' vecchio. */
    @SerialName("access") val access: TradeAccess? = null
)

/**
 * Cosa puo' fare l'utente in TradeRadar: 30 giorni di prova dall'attivazione,
 * poi Premium. Senza Premium, finita la prova, "solo ricevere": risponde alle
 * proposte e finisce gli scambi avviati, ma non sfoglia i match ne' ne manda
 * di nuove. [enforced] dice se il server lo fa gia' rispettare.
 */
@Serializable
data class TradeAccess(
    /** trial | premium | receive_only */
    @SerialName("mode") val mode: String? = null,
    @SerialName("trialEndsAt") val trialEndsAt: Long? = null,
    @SerialName("enforced") val enforced: Boolean? = null
) {
    val isTrial: Boolean get() = mode == "trial"
    val isReceiveOnly: Boolean get() = mode == "receive_only"

    /** Giorni di prova rimasti, arrotondati in su (l'ultimo giorno e' "1"). */
    fun trialDaysLeft(now: Long = Clock.System.now().toEpochMilliseconds()): Int {
        val end = trialEndsAt ?: return 0
        val left = end - now
        return if (left <= 0) 0 else ((left + DAY_MS - 1) / DAY_MS).toInt()
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

@Serializable
data class TradeHaveItem(
    @SerialName("key") val key: String? = null,
    @SerialName("variant") val variant: String? = null,
    @SerialName("condition") val condition: String? = null,
    @SerialName("language") val language: String? = null,
    @SerialName("qty") val qty: Int? = null,
    /** Carta singola messa in lista a mano (schema 3). */
    @SerialName("manual") val manual: Boolean? = null,
    /** Partecipa agli avvisi: sempre per i doppioni, a scelta per le carte a mano. */
    @SerialName("notify") val notify: Boolean? = null,
    /** Copie promesse in un accordo: restano mie, ma gli altri non le vedono (solo in GET). */
    @SerialName("reserved") val reserved: Int? = null
)

/** GET e PUT /v1/trade/haves */
@Serializable
data class TradeHavesPayload(
    @SerialName("items") val items: List<TradeHaveItem>? = null
)

@Serializable
data class TradeWantItem(
    @SerialName("key") val key: String,
    @SerialName("source") val source: String,
    @SerialName("priority") val priority: String = "nice"
)

/** PUT /v1/trade/wants */
@Serializable
data class TradeWantsRequest(
    @SerialName("items") val items: List<TradeWantItem>
)

/** PUT /v1/trade/owned */
@Serializable
data class TradeOwnedRequest(
    @SerialName("keys") val keys: List<String>,
    @SerialName("hash") val hash: String
)

@Serializable
data class TradeMatchItem(
    @SerialName("key") val key: String? = null,
    @SerialName("variant") val variant: String? = null,
    @SerialName("condition") val condition: String? = null,
    @SerialName("language") val language: String? = null,
    @SerialName("qty") val qty: Int? = null,
    /** "wanted" | "useful" | "possible" */
    @SerialName("level") val level: String? = null,
    /** "wishlist" | "album" | "set", o null */
    @SerialName("reason") val reason: String? = null,
    /** Nome italiano della carta e del set, dal catalogo; null se il server non li trova. */
    @SerialName("name") val name: String? = null,
    @SerialName("setName") val setName: String? = null,
    /** Solo con reason "set": carte del set possedute da chi la riceve, su quante. */
    @SerialName("setOwned") val setOwned: Int? = null,
    @SerialName("setSize") val setSize: Int? = null
)

@Serializable
data class TradeMatch(
    /** Id pubblico della persona: per proporle uno scambio o leggere le sue offerte. */
    @SerialName("id") val id: String? = null,
    @SerialName("nickname") val nickname: String? = null,
    /** "lt5" | "lt15" */
    @SerialName("distance") val distance: String? = null,
    @SerialName("tradesDone") val tradesDone: Int? = null,
    @SerialName("memberSince") val memberSince: Long? = null,
    @SerialName("reputation") val reputation: TradeReputation? = null,
    /** Il livello: bronze | silver | gold | platinum, o null (fase 2e). */
    @SerialName("tier") val tier: String? = null,
    @SerialName("level") val level: String? = null,
    @SerialName("mutual") val mutual: Boolean? = null,
    @SerialName("theyGive") val theyGive: List<TradeMatchItem>? = null,
    @SerialName("iGive") val iGive: List<TradeMatchItem>? = null,
    /** Quante carte in tutto per lato: le liste sopra ne portano al massimo 60. */
    @SerialName("theyGiveCount") val theyGiveCount: Int? = null,
    @SerialName("iGiveCount") val iGiveCount: Int? = null
)

/** Chi ha una carta, nella vista per carta. */
@Serializable
data class TradeCardHolder(
    /** Posizione del match in [TradeMatchesPayload.matches]. */
    @SerialName("match") val match: Int? = null,
    @SerialName("qty") val qty: Int? = null,
    @SerialName("variant") val variant: String? = null,
    @SerialName("condition") val condition: String? = null,
    @SerialName("language") val language: String? = null
)

/** Una carta che posso ricevere e chi ce l'ha: la vista per carta. */
@Serializable
data class TradeCardOffer(
    @SerialName("key") val key: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("setName") val setName: String? = null,
    @SerialName("level") val level: String? = null,
    @SerialName("reason") val reason: String? = null,
    @SerialName("setOwned") val setOwned: Int? = null,
    @SerialName("setSize") val setSize: Int? = null,
    /** Persone che ce l'hanno; [holders] ne elenca al massimo 50. */
    @SerialName("holderCount") val holderCount: Int? = null,
    @SerialName("holders") val holders: List<TradeCardHolder>? = null
)

/** GET /v1/trade/matches */
@Serializable
data class TradeMatchesPayload(
    @SerialName("paused") val paused: Boolean? = null,
    /** Sospeso: niente match finche' dura (fase 2f). */
    @SerialName("suspended") val suspended: Boolean? = null,
    /** Persone attive nella zona, anche senza carte per te. */
    @SerialName("nearby") val nearby: Int? = null,
    /** Le migliori 100, gia' in ordine di punteggio. */
    @SerialName("matches") val matches: List<TradeMatch>? = null,
    @SerialName("cards") val cards: List<TradeCardOffer>? = null
)

/** Corpo delle risposte d'errore: { "error": "no_profile" } e simili. */
@Serializable
data class TradeErrorPayload(
    @SerialName("error") val error: String? = null
)

// ── Proposte (fase 2a) ──────────────────────────────────────────────────────

/** Una carta in una proposta, o fra le offerte di un'altra persona. */
@Serializable
data class TradeOfferItem(
    @SerialName("key") val key: String? = null,
    @SerialName("variant") val variant: String? = null,
    @SerialName("condition") val condition: String? = null,
    @SerialName("language") val language: String? = null,
    @SerialName("qty") val qty: Int? = null,
    /** Dal catalogo, solo nelle risposte: il server li ignora nelle richieste. */
    @SerialName("name") val name: String? = null,
    @SerialName("setName") val setName: String? = null
)

/** GET /v1/trade/users/:id/haves */
@Serializable
data class TradeUserHavesPayload(
    @SerialName("items") val items: List<TradeOfferItem>? = null
)

/** POST /v1/trade/proposals: give e take dal punto di vista di chi manda. */
@Serializable
data class TradeProposalRequest(
    @SerialName("to") val to: String,
    @SerialName("give") val give: List<TradeOfferItem>,
    @SerialName("take") val take: List<TradeOfferItem>
)

/** POST /v1/trade/proposals/:id/counter */
@Serializable
data class TradeCounterRequest(
    @SerialName("give") val give: List<TradeOfferItem>,
    @SerialName("take") val take: List<TradeOfferItem>
)

@Serializable
data class TradeCreatedPayload(
    @SerialName("id") val id: String? = null
)

@Serializable
data class TradeCounterpart(
    /** L'id pubblico, lo stesso di [TradeMatch.id]. */
    @SerialName("id") val id: String? = null,
    @SerialName("nickname") val nickname: String? = null,
    /** "lt5" | "lt15" | "far" */
    @SerialName("distance") val distance: String? = null,
    @SerialName("tradesDone") val tradesDone: Int? = null,
    @SerialName("reputation") val reputation: TradeReputation? = null,
    @SerialName("tier") val tier: String? = null
)

/** Una proposta vista da me: [give] e' cio' che do, [take] cio' che ricevo. */
@Serializable
data class TradeProposal(
    @SerialName("id") val id: String? = null,
    /** "open" | "accepted" | "scheduled" | "done" | "no_show" | "expired" | "declined" | "cancelled" */
    @SerialName("status") val status: String? = null,
    /** Da 2 in su e' una controproposta. */
    @SerialName("revision") val revision: Int? = null,
    @SerialName("myTurn") val myTurn: Boolean? = null,
    /** Serve una mia mossa: rispondere, o confermare l'appuntamento. */
    @SerialName("actionNeeded") val actionNeeded: Boolean? = null,
    /** Chi ha gia' segnato "Scambio fatto" (fase 2c). */
    @SerialName("doneByMe") val doneByMe: Boolean? = null,
    @SerialName("doneByOther") val doneByOther: Boolean? = null,
    /** Chiuso da solo 7 giorni dopo l'appuntamento: uno solo dei due l'aveva segnato fatto. */
    @SerialName("autoClosed") val autoClosed: Boolean? = null,
    /** Mi hanno segnalato come assente e posso ancora rispondere "Io c'ero" (fino a [disputeUntil], ms). */
    @SerialName("canDispute") val canDispute: Boolean? = null,
    @SerialName("disputeUntil") val disputeUntil: Long? = null,
    /** La segnalazione di assenza e' stata contestata. */
    @SerialName("noShowDisputed") val noShowDisputed: Boolean? = null,
    @SerialName("closedAt") val closedAt: Long? = null,
    @SerialName("myRating") val myRating: TradeRating? = null,
    /** Il voto dell'altro: null finche' non ho votato anch'io (o non passano 7 giorni). */
    @SerialName("theirRating") val theirRating: TradeRating? = null,
    /** Il luogo dell'appuntamento l'ho gia' votato. */
    @SerialName("spotVoted") val spotVoted: Boolean? = null,
    @SerialName("meeting") val meeting: TradeMeeting? = null,
    @SerialName("iStarted") val iStarted: Boolean? = null,
    @SerialName("closedByMe") val closedByMe: Boolean? = null,
    @SerialName("createdAt") val createdAt: Long? = null,
    @SerialName("updatedAt") val updatedAt: Long? = null,
    @SerialName("counterpart") val counterpart: TradeCounterpart? = null,
    @SerialName("give") val give: List<TradeOfferItem>? = null,
    @SerialName("take") val take: List<TradeOfferItem>? = null
)

/** GET /v1/trade/proposals */
@Serializable
data class TradeProposalsPayload(
    @SerialName("proposals") val proposals: List<TradeProposal>? = null
)

// ── Luoghi e appuntamento (fase 2b) ─────────────────────────────────────────

@Serializable
data class TradeSpot(
    @SerialName("id") val id: String? = null,
    @SerialName("name") val name: String? = null,
    /** card_shop | comics | games | video_games | toys | mall | library | other */
    @SerialName("kind") val kind: String? = null,
    @SerialName("city") val city: String? = null,
    /** Solo per i luoghi segnalati: l'indirizzo scritto da chi l'ha segnalato. */
    @SerialName("address") val address: String? = null,
    @SerialName("openingHours") val openingHours: String? = null,
    @SerialName("lat") val lat: Double? = null,
    @SerialName("lon") val lon: Double? = null,
    /** Dal punto a meta' strada fra le due zone. */
    @SerialName("distanceKm") val distanceKm: Double? = null,
    /** Segnalato da un utente e non ancora approvato. */
    @SerialName("pending") val pending: Boolean? = null,
    /** Scambi chiusi qui, e i badge votati da almeno tre persone (tournaments | comics | card_shop). */
    @SerialName("trades") val trades: Int? = null,
    @SerialName("badges") val badges: List<String>? = null
)

@Serializable
data class TradeSlot(
    /** yyyy-MM-dd */
    @SerialName("day") val day: String,
    /** "HH:mm": l'ora dell'appuntamento (dal 01/10; le prime prove avevano solo la fascia). */
    @SerialName("time") val time: String? = null,
    /** morning | afternoon | evening: la parte della giornata, ricavata dall'ora. */
    @SerialName("part") val part: String? = null
)

@Serializable
data class TradeMeeting(
    /** none | proposed | confirmed */
    @SerialName("status") val status: String? = null,
    @SerialName("byMe") val byMe: Boolean? = null,
    @SerialName("spot") val spot: TradeSpot? = null,
    @SerialName("slots") val slots: List<TradeSlot>? = null,
    @SerialName("slot") val slot: TradeSlot? = null
)

/** Una zona che il server non ha ancora: il telefono la scarica da Overpass con [query]. */
@Serializable
data class TradeMissingCell(
    @SerialName("cell") val cell: String? = null,
    @SerialName("query") val query: String? = null
)

/** GET /v1/trade/proposals/:id/spots */
@Serializable
data class TradeSpotsPayload(
    @SerialName("spots") val spots: List<TradeSpot>? = null,
    @SerialName("missingCells") val missingCells: List<TradeMissingCell>? = null
)

/** POST /v1/trade/spots/cell: la risposta grezza di Overpass, il filtro lo fa il server. */
@Serializable
data class TradeCellUpload(
    @SerialName("cell") val cell: String,
    @SerialName("elements") val elements: kotlinx.serialization.json.JsonArray
)

@Serializable
data class TradeSpotCandidate(
    @SerialName("osmId") val osmId: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("kind") val kind: String? = null,
    @SerialName("city") val city: String? = null,
    @SerialName("lat") val lat: Double? = null,
    @SerialName("lon") val lon: Double? = null,
    @SerialName("distanceKm") val distanceKm: Double? = null
)

/** GET /v1/trade/spots/search */
@Serializable
data class TradeSpotSearchPayload(
    @SerialName("results") val results: List<TradeSpotCandidate>? = null
)

/** POST /v1/trade/spots: un luogo trovato con la ricerca, o una segnalazione (solo nome e citta'). */
@Serializable
data class TradeAddSpotRequest(
    @SerialName("osmId") val osmId: String? = null,
    @SerialName("name") val name: String,
    @SerialName("kind") val kind: String? = null,
    @SerialName("city") val city: String? = null,
    @SerialName("lat") val lat: Double? = null,
    @SerialName("lon") val lon: Double? = null,
    /** Per una segnalazione: facoltativo, da li' il server ricava le coordinate. */
    @SerialName("address") val address: String? = null
)

@Serializable
data class TradeAddSpotPayload(
    @SerialName("spot") val spot: TradeSpot? = null
)

/** POST /v1/trade/proposals/:id/meeting */
@Serializable
data class TradeMeetingRequest(
    @SerialName("spot") val spot: String,
    @SerialName("slots") val slots: List<TradeSlot>
)

/** POST /v1/trade/proposals/:id/meeting/confirm */
@Serializable
data class TradeConfirmRequest(
    @SerialName("slot") val slot: Int
)

// ── Chiusura e feedback (fase 2c) ───────────────────────────────────────────

/** Un voto: mood good | ok | bad, chip come "punctual" o "late". */
@Serializable
data class TradeRating(
    @SerialName("mood") val mood: String? = null,
    @SerialName("tags") val tags: List<String>? = null
)

@Serializable
data class TradeTagCount(
    @SerialName("tag") val tag: String? = null,
    @SerialName("count") val count: Int? = null
)

/** I voti ricevuti gia' visibili, e i chip positivi piu' ricevuti (i negativi non si mostrano). */
@Serializable
data class TradeReputation(
    @SerialName("good") val good: Int? = null,
    @SerialName("ok") val ok: Int? = null,
    @SerialName("bad") val bad: Int? = null,
    @SerialName("topTags") val topTags: List<TradeTagCount>? = null
)

/** POST /v1/trade/proposals/:id/rate */
@Serializable
data class TradeRateRequest(
    @SerialName("mood") val mood: String,
    @SerialName("tags") val tags: List<String>
)

/** POST /v1/trade/proposals/:id/spotvote: tournaments | comics | card_shop */
@Serializable
data class TradeSpotVoteRequest(
    @SerialName("tags") val tags: List<String>
)

// ── Classifica (fase 2e) ────────────────────────────────────────────────────

@Serializable
data class TradeLeaderboardEntry(
    @SerialName("rank") val rank: Int? = null,
    /** L'id pubblico, per segnalare o bloccare dal mini profilo. */
    @SerialName("id") val id: String? = null,
    @SerialName("nickname") val nickname: String? = null,
    /** bronze | silver | gold | platinum, o null */
    @SerialName("tier") val tier: String? = null,
    @SerialName("trades") val trades: Int? = null,
    @SerialName("positivePct") val positivePct: Int? = null,
    @SerialName("memberSince") val memberSince: Long? = null,
    @SerialName("isMe") val isMe: Boolean? = null,
    /** Per il mini profilo: persone diverse, voti visibili, chip piu' ricevuti. */
    @SerialName("partners") val partners: Int? = null,
    @SerialName("good") val good: Int? = null,
    @SerialName("ok") val ok: Int? = null,
    @SerialName("bad") val bad: Int? = null,
    @SerialName("topTags") val topTags: List<TradeTagCount>? = null,
    /** Il Pokemon scelto per il podio, null = l'iniziale. */
    @SerialName("avatar") val avatar: Int? = null,
    @SerialName("avatarAnimated") val avatarAnimated: Boolean? = null
)

/** Io: posizione (null se fuori), adesione (null = mai chiesto) e cosa manca per entrare. */
@Serializable
data class TradeLeaderboardMe(
    @SerialName("rank") val rank: Int? = null,
    @SerialName("optIn") val optIn: Boolean? = null,
    @SerialName("eligible") val eligible: Boolean? = null,
    @SerialName("trades") val trades: Int? = null,
    @SerialName("partners") val partners: Int? = null,
    @SerialName("positivePct") val positivePct: Int? = null,
    @SerialName("tier") val tier: String? = null,
    @SerialName("missingTrades") val missingTrades: Int? = null,
    @SerialName("missingPartners") val missingPartners: Int? = null
)

/** GET /v1/trade/leaderboard?scope=zone|italy */
@Serializable
data class TradeLeaderboardPayload(
    @SerialName("scope") val scope: String? = null,
    @SerialName("entries") val entries: List<TradeLeaderboardEntry>? = null,
    @SerialName("total") val total: Int? = null,
    @SerialName("me") val me: TradeLeaderboardMe? = null
)

/** PUT /v1/trade/avatar: avatar null = torna l'iniziale. */
@Serializable
data class TradeAvatarRequest(
    @SerialName("avatar") val avatar: Int?,
    @SerialName("animated") val animated: Boolean
)

/** PUT /v1/trade/leaderboard/optin */
@Serializable
data class TradeOptInRequest(
    @SerialName("optIn") val optIn: Boolean
)

// ── Segnala e blocca (fase 2f) ──────────────────────────────────────────────

/** POST /v1/trade/users/:id/report. reason: behavior | scam | fake_cards | nickname | other */
@Serializable
data class TradeReportRequest(
    @SerialName("reason") val reason: String,
    @SerialName("note") val note: String,
    @SerialName("proposalId") val proposalId: String?,
    @SerialName("block") val block: Boolean
)

@Serializable
data class TradeBlockedUser(
    @SerialName("id") val id: String? = null,
    @SerialName("nickname") val nickname: String? = null,
    @SerialName("blockedAt") val blockedAt: Long? = null
)

/** GET /v1/trade/blocks */
@Serializable
data class TradeBlocksPayload(
    @SerialName("items") val items: List<TradeBlockedUser>? = null
)

// ── Notifiche (fase 3) ──────────────────────────────────────────────────────

/** wants null = mai chiesto: niente avvisi sulle carte cercate finche' non si risponde. */
@Serializable
data class TradeNotifyPrefs(
    @SerialName("proposals") val proposals: Boolean? = null,
    @SerialName("meetings") val meetings: Boolean? = null,
    @SerialName("reminders") val reminders: Boolean? = null,
    @SerialName("after") val after: Boolean? = null,
    @SerialName("wants") val wants: Boolean? = null
)

/** PUT /v1/trade/push */
@Serializable
data class TradePushRequest(
    @SerialName("token") val token: String,
    @SerialName("lang") val lang: String
)
