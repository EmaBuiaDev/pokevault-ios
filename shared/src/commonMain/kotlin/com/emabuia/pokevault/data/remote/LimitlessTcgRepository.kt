package com.emabuia.pokevault.data.remote

import com.emabuia.pokevault.data.FileCache
import com.emabuia.pokevault.data.model.MetaArchetype
import com.emabuia.pokevault.data.model.MetaDeck
import com.emabuia.pokevault.data.model.MetaDeckCard
import com.emabuia.pokevault.data.model.TournamentKind
import com.emabuia.pokevault.data.model.TournamentResult
import io.ktor.client.HttpClient
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Meta Deck e Win Tournament: data/remote/LimitlessTcgRepository.kt di
 * Android, con la stessa logica (stesse finestre, soglie e cache).
 *
 * Tutto il lavoro gira su un solo thread alla volta ([confined]): le cache
 * in memoria e il limitatore delle richieste sono mappe semplici, e le
 * chiamate di rete restano comunque in parallelo perche' sono sospese.
 * Su disco la cache va nella cartella dati ([store]), come i file di Android.
 */
@OptIn(ExperimentalTime::class)
class LimitlessTcgRepository internal constructor(
    private val api: LimitlessApi,
    private val store: FileCache,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    constructor(client: HttpClient, store: FileCache) : this(LimitlessApi(client, LimitlessRateLimiter()), store)

    private val confined = Dispatchers.Default.limitedParallelism(1)

    private data class Timed<T>(val value: T, val timestamp: Long)

    private val archetypeCache = mutableMapOf<String, Timed<List<MetaArchetype>>>()
    private val tournamentResultsCache = mutableMapOf<String, Timed<List<TournamentResult>>>()
    private val tournamentListCache = mutableMapOf<String, Timed<List<LimitlessTournament>>>()
    /** Dettagli per id, senza scadenza: un torneo concluso non cambia. */
    private val detailsCache = mutableMapOf<String, LimitlessTournamentDetails>()
    /** Standings per torneo, condivisi fra archetipi e Win Tournament (solo in memoria). */
    private val standingsCache = mutableMapOf<String, List<LimitlessStanding>>()
    private var detailsLoadedFromDisk = false

    /** Il momento piu' recente di una voce in cache per il formato, o null. */
    fun lastCacheTimestamp(format: String): Long? {
        val inMemory = listOfNotNull(
            archetypeCache[archetypesKey(format)]?.timestamp,
            tournamentResultsCache.entries
                .filter { it.key.startsWith("results_v2_${format}_") }
                .maxOfOrNull { it.value.timestamp }
        ).maxOrNull()
        val onDisk = timestampOnDisk(archetypesKey(format))
        return listOfNotNull(inMemory, onDisk).maxOrNull()
    }

    fun rateLimitRetryAfterSeconds(): Long = api.retryAfterSeconds()

    // ── Archetipi (Meta Deck) ───────────────────────────────────────────────

    /** Gli archetipi del meta, aggregati dagli ultimi tornei (come limitlesstcg.com/decks). */
    suspend fun getMetaArchetypes(format: String = "standard"): Result<List<MetaArchetype>> =
        withContext(confined) { loadMetaArchetypes(format) }

    private suspend fun loadMetaArchetypes(format: String): Result<List<MetaArchetype>> {
        val cacheKey = archetypesKey(format)
        archetypeCache[cacheKey]?.let { cached ->
            if (now() - cached.timestamp < CACHE_DURATION) return Result.success(cached.value)
        }
        readFromDisk(cacheKey, ARCHETYPES, CACHE_DURATION)?.let { fresh ->
            archetypeCache[cacheKey] = Timed(fresh, timestampOnDisk(cacheKey) ?: 0L)
            return Result.success(fresh)
        }

        return try {
            val apiFormat = if (format.lowercase() == "expanded") "expanded" else "standard"
            val tournaments = getTournamentCandidates(apiFormat).take(ARCHETYPE_TOURNAMENTS)
            if (tournaments.isEmpty()) return Result.success(emptyList())

            data class DeckEntry(val archetype: String, val placement: Int, val winrate: Double?, val metaDeck: MetaDeck)

            val allEntries = coroutineScope {
                tournaments.map { tournament ->
                    async {
                        try {
                            val withDeck = LimitlessPlacing.ranked(
                                standingsOf(tournament.id).filter { it.deck?.name != null || it.decklist != null }
                            ) { it.placingOrZero }.take(32)

                            withDeck.mapNotNull { standing ->
                                val archName = standing.deck?.name
                                    ?: LimitlessDecklists.inferArchetype(LimitlessDecklists.parse(standing.decklist))
                                if (archName.isBlank() || archName == "Unknown") return@mapNotNull null
                                DeckEntry(archName, standing.placingOrZero, standing.winrate(), mapToMetaDeck(standing, tournament))
                            }
                        } catch (e: Exception) {
                            if (e is CancellationException || e is LimitlessRateLimitException) throw e
                            emptyList()
                        }
                    }
                }.awaitAll().flatten()
            }
            if (allEntries.isEmpty()) return Result.success(emptyList())

            val totalDecks = allEntries.size
            val archetypes = allEntries.groupBy { it.archetype.lowercase().trim() }.map { (_, entries) ->
                val winrates = entries.mapNotNull { it.winrate }
                val ranked = LimitlessPlacing.ranked(entries) { it.placement }
                MetaArchetype(
                    name = entries.first().archetype,
                    count = entries.size,
                    metaShare = (entries.size.toDouble() / totalDecks) * 100.0,
                    avgWinrate = if (winrates.isNotEmpty()) winrates.average() else 0.0,
                    topPlacement = LimitlessPlacing.best(entries.map { it.placement }),
                    recentResults = ranked.map { it.placement }.filter(LimitlessPlacing::isKnown).take(5),
                    // Il deck col miglior piazzamento come esempio da importare.
                    sampleDeck = ranked.firstOrNull()?.metaDeck
                )
            }.sortedByDescending { it.metaShare }

            archetypeCache[cacheKey] = Timed(archetypes, now())
            writeToDisk(cacheKey, ARCHETYPES, archetypes)
            Result.success(archetypes)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Dati vecchi invece di un errore: a finestra chiusa valgono piu' di niente.
            archetypeCache[cacheKey]?.let { return Result.success(it.value) }
            readFromDisk(cacheKey, ARCHETYPES, maxAgeMs = null)?.let { return Result.success(it) }
            Result.failure(e)
        }
    }

    // ── Tornei (Win Tournament) ─────────────────────────────────────────────

    /**
     * Gli ultimi [limit] tornei con i primi tre, filtrati per [kind]. Il dato
     * "online o dal vivo" sta solo nel dettaglio di ogni torneo: si chiedono a
     * gruppi, con un tetto di dettagli nuovi per caricamento (vedi Android).
     */
    suspend fun getTournamentResults(
        format: String = "standard",
        limit: Int = 10,
        kind: TournamentKind = TournamentKind.ALL
    ): Result<List<TournamentResult>> = withContext(confined) { loadTournamentResults(format, limit, kind) }

    private suspend fun loadTournamentResults(format: String, limit: Int, kind: TournamentKind): Result<List<TournamentResult>> {
        val cacheKey = "results_v2_${format}_${limit}_${kind.name}"
        tournamentResultsCache[cacheKey]?.let { cached ->
            if (now() - cached.timestamp < CACHE_DURATION) return Result.success(cached.value)
        }
        readFromDisk(cacheKey, RESULTS, CACHE_DURATION)?.let { fresh ->
            tournamentResultsCache[cacheKey] = Timed(fresh, timestampOnDisk(cacheKey) ?: 0L)
            return Result.success(fresh)
        }

        return try {
            val apiFormat = if (format.lowercase() == "expanded") "expanded" else "standard"
            // Un "torneo" da quattro giocatori e' una serata fra amici, non un risultato.
            val candidates = getTournamentCandidates(apiFormat).filter { it.players >= MIN_TOURNAMENT_PLAYERS }
            if (candidates.isEmpty()) return Result.success(emptyList())

            var newDetailsBudget = MAX_NEW_DETAILS_PER_LOAD
            val matched = mutableListOf<Pair<LimitlessTournament, LimitlessTournamentDetails?>>()

            for (chunk in candidates.chunked(DETAILS_CONCURRENCY)) {
                val missing = chunk.filter { cachedDetails(it.id) == null }
                if (missing.size > newDetailsBudget) {
                    // Questo gruppo costa troppo: solo quello che e' gia' noto, e avanti.
                    matched += chunk
                        .mapNotNull { tournament -> cachedDetails(tournament.id)?.let { tournament to it } }
                        .filter { (_, details) -> kind.accepts(details) }
                } else {
                    newDetailsBudget -= missing.size
                    val resolved = coroutineScope {
                        chunk.map { tournament -> async { tournament to tournamentDetails(tournament.id) } }.awaitAll()
                    }
                    matched += resolved.filter { (_, details) -> kind.accepts(details) }
                }
                if (matched.size >= limit) break
            }

            val selected = matched.take(limit)
            if (selected.isEmpty()) {
                tournamentResultsCache[cacheKey] = Timed(emptyList(), now())
                return Result.success(emptyList())
            }

            val results = coroutineScope {
                selected.map { (tournament, details) ->
                    async {
                        try {
                            val withDecklist = LimitlessPlacing.ranked(
                                standingsOf(tournament.id).filter { it.decklist != null }
                            ) { it.placingOrZero }
                            // Prima i piazzamenti 1-3; se mancano, i primi tre con decklist.
                            val top3 = withDecklist.filter { it.placingOrZero in 1..3 }.take(3)
                                .ifEmpty { withDecklist.take(3) }

                            TournamentResult(
                                tournamentId = tournament.id,
                                tournamentName = tournament.name.ifEmpty { tournament.id },
                                date = tournament.date.ifEmpty { null },
                                players = tournament.players,
                                top3 = top3.map { mapToMetaDeck(it, tournament) }.filter { it.cards.isNotEmpty() },
                                isOnline = details?.isOnline,
                                organizerName = details?.organizer?.name?.ifBlank { null },
                                organizerLogo = details?.organizer?.logo?.ifBlank { null }
                            )
                        } catch (e: Exception) {
                            if (e is CancellationException || e is LimitlessRateLimitException) throw e
                            null
                        }
                    }
                }.awaitAll().filterNotNull().filter { it.top3.isNotEmpty() }
            }

            val sorted = results.sortedByDescending { it.date }
            tournamentResultsCache[cacheKey] = Timed(sorted, now())
            writeToDisk(cacheKey, RESULTS, sorted)
            Result.success(sorted)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            tournamentResultsCache[cacheKey]?.let { return Result.success(it.value) }
            readFromDisk(cacheKey, RESULTS, maxAgeMs = null)?.let { return Result.success(it) }
            Result.failure(e)
        }
    }

    /** La finestra di tornei recenti, condivisa da archetipi e Win Tournament. */
    private suspend fun getTournamentCandidates(apiFormat: String): List<LimitlessTournament> {
        val cacheKey = "candidates_$apiFormat"
        tournamentListCache[cacheKey]?.let { cached ->
            if (now() - cached.timestamp < CACHE_DURATION) return cached.value
        }
        readFromDisk(cacheKey, CANDIDATES, CACHE_DURATION)?.let { fresh ->
            tournamentListCache[cacheKey] = Timed(fresh, timestampOnDisk(cacheKey) ?: 0L)
            return fresh
        }
        return try {
            val tournaments = api.getTournaments(game = "PTCG", format = apiFormat, limit = TOURNAMENT_CANDIDATE_WINDOW)
            tournamentListCache[cacheKey] = Timed(tournaments, now())
            writeToDisk(cacheKey, CANDIDATES, tournaments)
            tournaments
        } catch (e: LimitlessRateLimitException) {
            // La finestra scaduta vale comunque piu' di niente.
            readFromDisk(cacheKey, CANDIDATES, maxAgeMs = null) ?: throw e
        }
    }

    /** Il dettaglio di un torneo, tenuto per sempre (anche su disco). */
    private suspend fun tournamentDetails(tournamentId: String): LimitlessTournamentDetails? {
        cachedDetails(tournamentId)?.let { return it }
        return try {
            val details = api.getTournamentDetails(tournamentId)
            detailsCache[tournamentId] = details
            store.write(KEY_DETAILS, DETAILS, detailsCache.entries.take(MAX_DETAILS).associate { it.key to it.value })
            details
        } catch (e: LimitlessRateLimitException) {
            throw e
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    private fun cachedDetails(tournamentId: String): LimitlessTournamentDetails? {
        if (!detailsLoadedFromDisk) {
            detailsLoadedFromDisk = true
            store.read(KEY_DETAILS, DETAILS)?.data?.forEach { (id, details) -> detailsCache.getOrPut(id) { details } }
        }
        return detailsCache[tournamentId]
    }

    private suspend fun standingsOf(tournamentId: String): List<LimitlessStanding> {
        standingsCache[tournamentId]?.let { return it }
        val standings = api.getTournamentStandings(tournamentId)
        standingsCache[tournamentId] = standings
        return standings
    }

    /** Il refresh manuale: via le liste calcolate, non i dettagli e gli standings di eventi conclusi. */
    suspend fun clearCache() = withContext(confined) {
        archetypeCache.clear()
        tournamentResultsCache.clear()
        tournamentListCache.clear()
        COMPUTED_KEYS.forEach { prefix -> knownDiskKeys.filter { it.startsWith(prefix) }.forEach(store::remove) }
        knownDiskKeys.clear()
    }

    private fun mapToMetaDeck(standing: LimitlessStanding, tournament: LimitlessTournament): MetaDeck {
        val cards = LimitlessDecklists.parse(standing.decklist)
        val displayName = standing.name.ifEmpty { standing.player }
        return MetaDeck(
            id = "${tournament.id}_${standing.player.ifEmpty { standing.name }}_${standing.placingOrZero}",
            archetype = standing.deck?.name ?: LimitlessDecklists.inferArchetype(cards),
            player = displayName.ifEmpty { null },
            tournament = tournament.name.ifEmpty { null },
            tournamentId = tournament.id.ifEmpty { null },
            date = tournament.date.ifEmpty { null },
            placement = standing.placing?.takeIf { it > 0 },
            winrate = standing.winrate(),
            link = if (tournament.id.isNotEmpty() && standing.player.isNotEmpty())
                "https://play.limitlesstcg.com/tournament/${tournament.id}/player/${standing.player}"
            else null,
            cards = cards
        )
    }

    private fun LimitlessStanding.winrate(): Double? {
        val record = record ?: return null
        val total = record.wins + record.losses + record.ties
        return if (total > 0) record.wins.toDouble() / total else null
    }

    // ── Cache su disco ──────────────────────────────────────────────────────

    /** Le chiavi scritte in questa sessione, per poterle togliere col refresh. */
    private val knownDiskKeys = mutableSetOf<String>()

    private fun <T> readFromDisk(key: String, serializer: KSerializer<List<T>>, maxAgeMs: Long?): List<T>? {
        val cached = store.read(diskKey(key), serializer) ?: return null
        knownDiskKeys += diskKey(key)
        if (maxAgeMs != null && now() - cached.savedAt > maxAgeMs) return null
        return cached.data.takeIf { it.isNotEmpty() }
    }

    private fun <T> writeToDisk(key: String, serializer: KSerializer<List<T>>, value: List<T>) {
        store.write(diskKey(key), serializer, value)
        knownDiskKeys += diskKey(key)
    }

    private fun timestampOnDisk(key: String): Long? =
        store.read(diskKey(key), JsonElement.serializer())?.savedAt?.takeIf { it > 0 }

    private fun diskKey(key: String) = "limitless_" + key.replace(NOT_SAFE, "_")

    private companion object {
        const val CACHE_DURATION = 30 * 60 * 1000L
        const val MIN_TOURNAMENT_PLAYERS = 8
        const val TOURNAMENT_CANDIDATE_WINDOW = 60
        const val DETAILS_CONCURRENCY = 3
        const val MAX_NEW_DETAILS_PER_LOAD = 14
        const val ARCHETYPE_TOURNAMENTS = 12
        const val MAX_DETAILS = 400
        const val KEY_DETAILS = "limitless_details"
        val COMPUTED_KEYS = listOf("limitless_archetypes", "limitless_results", "limitless_candidates")
        val NOT_SAFE = Regex("[^A-Za-z0-9_-]")

        val ARCHETYPES = ListSerializer(MetaArchetype.serializer())
        val RESULTS = ListSerializer(TournamentResult.serializer())
        val CANDIDATES = ListSerializer(LimitlessTournament.serializer())
        val DETAILS = MapSerializer(String.serializer(), LimitlessTournamentDetails.serializer())

        // v2: le voci di prima contavano i ritirati come piazzamento 0.
        fun archetypesKey(format: String) = "archetypes_v2_$format"
    }
}

private fun TournamentKind.accepts(details: LimitlessTournamentDetails?): Boolean = when (this) {
    TournamentKind.ALL -> true
    TournamentKind.LIVE -> details?.isOnline == false
    TournamentKind.ONLINE -> details?.isOnline == true
}

/**
 * La decklist di uno standing, nelle forme in cui l'API la restituisce:
 * parseDecklistCards e parseCardList di Android, su JSON invece che su Gson.
 *
 * 1. mappa per categoria: {"pokemon": [...], "trainer": [...], "energy": [...]}
 * 2. lista piatta: [{"count":4,"name":"...","set":"...","number":"..."}]
 * 3. {"deck": [{"id":"OBF_125","count":4}]}
 */
internal object LimitlessDecklists {

    private val CATEGORY_KEYS = listOf("pokemon" to "pokemon", "pokémon" to "pokemon", "trainer" to "trainer", "energy" to "energy")

    fun parse(decklist: JsonElement?): List<MetaDeckCard> {
        when (decklist) {
            is JsonObject -> {
                val byCategory = CATEGORY_KEYS.flatMap { (key, type) ->
                    (decklist[key] as? JsonArray)?.let { parseCardList(it, type) }.orEmpty()
                }
                if (byCategory.isNotEmpty()) return byCategory
                (decklist["deck"] as? JsonArray)?.let { parseCardList(it, null) }?.takeIf { it.isNotEmpty() }?.let { return it }
            }
            is JsonArray -> return parseCardList(decklist, null)
            else -> {}
        }
        return emptyList()
    }

    private fun parseCardList(items: JsonArray, forcedType: String?): List<MetaDeckCard> = items.mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        val count = item.int("count") ?: item.int("amount") ?: 1

        val name = item.string("name").orEmpty()
        val set = item.string("set").orEmpty()
        val number = item.string("number").orEmpty()
        val cardId = item.string("id").orEmpty()

        val finalName: String
        val finalSet: String
        val finalNumber: String
        if (name.isNotEmpty()) {
            finalName = name
            finalSet = SetCodeMapper.normalizeDecklistSetCode(set) ?: set
            finalNumber = number
        } else if (cardId.isNotEmpty()) {
            // "OBF_125": set e numero dall'id, e l'id come nome di ripiego.
            val parts = cardId.split("_", limit = 2)
            val parsedSet = parts.getOrElse(0) { "" }
            finalSet = SetCodeMapper.normalizeDecklistSetCode(parsedSet) ?: parsedSet
            finalNumber = parts.getOrElse(1) { "" }
            finalName = cardId
        } else {
            return@mapNotNull null
        }

        MetaDeckCard(
            name = finalName,
            set = finalSet.ifEmpty { null },
            number = finalNumber.ifEmpty { null },
            qty = count,
            type = forcedType ?: classifyCardByName(finalName)
        )
    }

    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.int(key: String): Int? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        return primitive.intOrNull ?: primitive.doubleOrNull?.toInt() ?: primitive.contentOrNull?.toIntOrNull()
    }

    private fun classifyCardByName(name: String): String {
        val nameLower = name.lowercase()
        return when {
            nameLower.contains("energy") || nameLower.contains("energia") -> "energy"
            listOf(
                "professor", "boss", "judge", "research", "iono", "nest ball", "ultra ball", "rare candy",
                "switch", "catcher", "pal pad", "battle vip pass", "tool", "stadium", "supporter", "item"
            ).any { nameLower.contains(it) } -> "trainer"
            else -> "pokemon"
        }
    }

    /** I due Pokemon con piu' copie, come indicatore dell'archetipo. */
    fun inferArchetype(cards: List<MetaDeckCard>): String {
        val pokemonCards = cards.filter { it.type == "pokemon" }.sortedByDescending { it.qty }
        return when {
            pokemonCards.size >= 2 -> pokemonCards.take(2).joinToString(" / ") { it.name.split(" ").first() }
            pokemonCards.size == 1 -> pokemonCards.first().name
            else -> "Unknown"
        }
    }
}
