package com.emabuia.pokevault.data

import com.emabuia.pokevault.data.italian.ItalianCardFacets
import com.emabuia.pokevault.data.italian.ItalianExpansionFacet
import com.emabuia.pokevault.data.italian.ItalianHpBucket
import com.emabuia.pokevault.data.italian.ItalianPrintedTotals
import com.emabuia.pokevault.data.italian.ItalianSearchFacets
import com.emabuia.pokevault.data.local.ItalianTranslations
import com.emabuia.pokevault.data.remote.CardImages
import com.emabuia.pokevault.data.remote.CardMarket
import com.emabuia.pokevault.data.remote.CardMarketPrices
import com.emabuia.pokevault.data.remote.SetCodeMapper
import com.emabuia.pokevault.data.remote.SetImages
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.data.remote.TcgPlayer
import com.emabuia.pokevault.data.remote.TcgPriceInfo
import com.emabuia.pokevault.data.remote.TcgSet
import com.emabuia.pokevault.data.model.CardOptions
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Il pezzo di CatalogRepository dell'app Android che il Pokedex usa (elenco
 * espansioni, ricerca carte per nome e per numero, vocabolario dei filtri,
 * carte di un'espansione), costruito sul catalogo italiano del Worker.
 *
 * Restituisce gli stessi oggetti di Android, con gli stessi id: un'espansione
 * e' "<codice>__ita" ([italianSetId]), una carta "ita:<codice>:<numero>". Cosi'
 * i ViewModel e le schermate portati da li' restano quelli. Su Android l'elenco
 * mescolava i set inglesi di PokeWallet; il Pokedex pero' mostra solo quelli
 * italiani, che qui sono gli unici.
 *
 * I prezzi sono quelli del Worker (/ita/prices), con medie e link Cardmarket:
 * su Android lo snapshot italiano e, per le carte scoperte, PokeWallet.
 */
class PokedexCatalog(private val catalog: CatalogRepository) {

    /** Le espansioni come TcgSet, dalla piu' recente. */
    suspend fun getSets(forceRefresh: Boolean = false): Result<List<TcgSet>> = try {
        if (forceRefresh) catalog.refresh()
        val expansions = catalog.expansionList()
        if (expansions.isEmpty()) {
            val error = (catalog.expansions.value as? ExpansionsState.Error)?.message
            Result.failure(IllegalStateException(error ?: "Nessuna espansione"))
        } else {
            Result.success(expansions.map(::toTcgSet))
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Result.failure(e)
    }

    suspend fun getSetInfo(setId: String): Result<TcgSet> {
        val expansionId = expansionIdOf(setId)
        val expansion = catalog.expansionList().firstOrNull { it.id.equals(expansionId, ignoreCase = true) }
            ?: return Result.failure(NoSuchElementException(setId))
        return Result.success(toTcgSet(expansion))
    }

    /** Le carte di un'espansione, in ordine di numero, con i prezzi del Worker. */
    suspend fun getCardsBySet(setId: String): Result<List<TcgCard>> = try {
        val expansionId = expansionIdOf(setId)
        val setInfo = getSetInfo(setId).getOrElse { TcgSet(id = italianSetId(expansionId), name = expansionId.uppercase(), language = "ITA") }
        val loaded = catalog.expansionCards(expansionId)
        Result.success(loaded.cards.map { card -> toItalianTcgCard(card, setInfo, loaded.priceOf(card)) })
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Result.failure(e)
    }

    /**
     * Le carte per id ("ita:me05:4"), coi prezzi: getCard di Android, ma in un
     * colpo solo. Gli id che il catalogo italiano non conosce restano fuori.
     */
    suspend fun getCardsByIds(ids: Collection<String>): Map<String, TcgCard> = try {
        val found = catalog.italianCardsById(ids)
        if (found.isEmpty()) emptyMap() else withPrices(found.values.toList(), setsById()).associateBy { it.id }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        emptyMap()
    }

    /** I prezzi di un'espansione per numero di carta, come li legge il Pokedex. */
    suspend fun getSetPriceMap(setId: String): Map<String, PriceEntry> = catalog.pricesOf(expansionIdOf(setId))

    /** searchItalianCardsByName di Android: lo stesso punteggio ([CatalogSearch]). */
    suspend fun searchItalianCardsByName(query: String, limit: Int = 60, targetSetId: String? = null): Result<List<TcgCard>> = try {
        val cleanQuery = CatalogSearch.sanitizeQuery(query)
        val normalizedQuery = CatalogSearch.normalizeNameForLookup(cleanQuery)
        if (normalizedQuery.isBlank()) {
            Result.success(emptyList())
        } else {
            val cards = catalog.allCards()
            val sets = setsById()
            val normalizedTargetSet = targetSetId?.let(SetCodeMapper::normalizeDecklistSetCode)?.lowercase()?.takeIf { it.isNotBlank() }
            val found = withContext(Dispatchers.Default) {
                val queryTokens = normalizedQuery.split(" ").filter { it.isNotBlank() }
                cards.asSequence()
                    .filter { card ->
                        normalizedTargetSet == null ||
                            ItalianCardLookup.matchesItalianExpansionHint(card.espansioneId.trim().lowercase(), normalizedTargetSet)
                    }
                    .mapNotNull { card ->
                        val normalizedName = CatalogSearch.normalizeNameForLookup(card.nome)
                        if (normalizedName.isBlank()) return@mapNotNull null
                        val score = CatalogSearch.scoreItalianNameMatch(normalizedName, normalizedQuery, queryTokens)
                        if (score <= 0) null else card to score
                    }
                    .sortedWith(
                        compareByDescending<Pair<Card, Int>> { it.second }
                            .thenBy { it.first.number?.toIntOrNull() ?: Int.MAX_VALUE }
                            .thenBy { it.first.espansioneId.lowercase() }
                    )
                    .take(limit.coerceIn(1, 300))
                    .toList()
            }
            Result.success(withPrices(found.map { it.first }, sets))
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Result.failure(e)
    }

    /**
     * searchItalianCardsByNumber di Android: le carte col numero stampato, e il
     * totale dopo la barra che pesa senza escludere ([ItalianPrintedTotals]).
     */
    suspend fun searchItalianCardsByNumber(
        number: String,
        printedTotal: Int? = null,
        targetSetId: String? = null,
        limit: Int = 300,
    ): Result<List<TcgCard>> = try {
        val target = number.trimStart('0').ifBlank { number }
        if (target.isBlank()) {
            Result.success(emptyList())
        } else {
            val cards = catalog.allCards()
            val expansions = catalog.expansionList().associateBy { it.id.lowercase() }
            val sets = setsById()
            val normalizedTargetSet = targetSetId?.let(SetCodeMapper::normalizeDecklistSetCode)?.lowercase()?.takeIf { it.isNotBlank() }
            val found = withContext(Dispatchers.Default) {
                val matching = cards.filter { card ->
                    val expansionId = card.espansioneId.trim().lowercase()
                    if (normalizedTargetSet != null && !ItalianCardLookup.matchesItalianExpansionHint(expansionId, normalizedTargetSet)) {
                        return@filter false
                    }
                    (card.number ?: "").trimStart('0').ifBlank { "0" }.equals(target, ignoreCase = true)
                }
                val scored = matching.map { card ->
                    card to ItalianPrintedTotals.matchScore(
                        knownTotals = printedTotalCandidates(expansions[card.espansioneId.trim().lowercase()]),
                        typedTotal = printedTotal,
                    )
                }
                ItalianPrintedTotals.keepBestMatches(scored)
                    .sortedBy { it.espansioneId }
                    .take(limit.coerceIn(1, 300))
            }
            Result.success(withPrices(found, sets))
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Result.failure(e)
    }

    private var facetsCache: ItalianSearchFacets? = null

    /** getItalianSearchFacets di Android: il vocabolario dei filtri, scandendo tutto il catalogo. */
    suspend fun getItalianSearchFacets(): Result<ItalianSearchFacets> {
        facetsCache?.let { return Result.success(it) }
        return try {
            val cards = catalog.allCards()
            val expansions = catalog.expansionList()
            val names = expansions.associate { it.id.lowercase() to it.name }
            val series = expansions.associate { it.id.lowercase() to ItalianTranslations.translateSeriesName(it.series.orEmpty()) }
            val facets = withContext(Dispatchers.Default) {
                val counts = mutableMapOf<String, Int>()
                fun bump(dimension: String, value: String) {
                    if (value.isBlank()) return
                    counts["$dimension:$value"] = (counts["$dimension:$value"] ?: 0) + 1
                }
                val expansionCardCounts = mutableMapOf<String, Int>()
                cards.forEach { record ->
                    bump(ItalianCardFacets.DIMENSION_SUPERTYPE, ItalianCardFacets.supertypeOf(record))
                    ItalianCardFacets.typesOf(record).forEach { bump(ItalianCardFacets.DIMENSION_TYPE, it) }
                    ItalianCardFacets.rarityOf(record)?.let { bump(ItalianCardFacets.DIMENSION_RARITY, it) }
                    bump(ItalianCardFacets.DIMENSION_VARIANT, ItalianCardFacets.variantOf(record))
                    ItalianCardFacets.hpBucketOf(record)?.let { bump(ItalianCardFacets.DIMENSION_HP, it.key) }
                    val expansionId = ItalianCardFacets.expansionIdOf(record)
                    if (expansionId.isNotBlank()) {
                        bump(ItalianCardFacets.DIMENSION_EXPANSION, expansionId)
                        expansionCardCounts[expansionId] = (expansionCardCounts[expansionId] ?: 0) + 1
                    }
                }
                fun distinctFor(dimension: String): List<String> =
                    counts.keys.asSequence()
                        .filter { it.startsWith("$dimension:") }
                        .map { it.removePrefix("$dimension:") }
                        .sortedByDescending { counts["$dimension:$it"] ?: 0 }
                        .toList()
                ItalianSearchFacets(
                    supertypes = distinctFor(ItalianCardFacets.DIMENSION_SUPERTYPE),
                    types = distinctFor(ItalianCardFacets.DIMENSION_TYPE).sorted(),
                    rarities = distinctFor(ItalianCardFacets.DIMENSION_RARITY),
                    variants = distinctFor(ItalianCardFacets.DIMENSION_VARIANT),
                    hpBuckets = ItalianHpBucket.entries.filter { (counts["${ItalianCardFacets.DIMENSION_HP}:${it.key}"] ?: 0) > 0 },
                    expansions = expansionCardCounts.entries
                        .map { (id, count) ->
                            val label = names[id] ?: id.uppercase()
                            ItalianExpansionFacet(id = id, label = label, series = series[id].orEmpty(), cardCount = count)
                        }
                        .sortedWith(compareByDescending<ItalianExpansionFacet> { it.cardCount }.thenBy { it.label }),
                )
            }
            facetsCache = facets
            Result.success(facets)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    // ── Conversioni ─────────────────────────────────────────────────────────

    private suspend fun setsById(): Map<String, TcgSet> =
        catalog.expansionList().associate { it.id.lowercase() to toTcgSet(it) }

    /** Le carte trovate col prezzo del Worker: un file per espansione, letto una volta sola. */
    private suspend fun withPrices(cards: List<Card>, sets: Map<String, TcgSet>): List<TcgCard> {
        val pricesByExpansion = cards.map { it.espansioneId.trim().lowercase() }.distinct()
            .associateWith { runCatching { catalog.pricesOf(it) }.getOrDefault(emptyMap()) }
        return cards.map { card ->
            val expansionId = card.espansioneId.trim().lowercase()
            val setInfo = sets[expansionId] ?: TcgSet(id = italianSetId(expansionId), name = expansionId.uppercase(), language = "ITA")
            toItalianTcgCard(card, setInfo, card.number?.let { pricesByExpansion[expansionId]?.get(it) })
        }.distinctBy { it.id }
    }

    private fun printedTotalCandidates(expansion: Expansion?): Set<Int> {
        if (expansion == null) return emptySet()
        return setOfNotNull(
            ITALIAN_PRINTED_TOTAL_BY_EXPANSION[expansion.id.lowercase()],
            expansion.officialCount?.takeIf { it > 0 },
            expansion.cardCount.takeIf { it > 0 },
        )
    }

    companion object {
        /** Il suffisso degli id delle espansioni italiane, come su Android. */
        const val ITALIAN_SET_SUFFIX = "__ita"

        /** Totali stampati corretti a mano dove il dato del catalogo e' sbagliato (Android). */
        private val ITALIAN_PRINTED_TOTAL_BY_EXPANSION = mapOf("me04" to 87)

        fun italianSetId(expansionId: String): String = expansionId.trim().lowercase() + ITALIAN_SET_SUFFIX

        /** "sv08__ita" -> "sv08"; un id senza suffisso resta com'e'. */
        fun expansionIdOf(setId: String): String = setId.substringBefore(ITALIAN_SET_SUFFIX).trim().lowercase()

        /** buildItalianTcgSet di Android: nome, serie e data sono quelli di D1, il logo e' su R2. */
        fun toTcgSet(expansion: Expansion): TcgSet {
            val total = ITALIAN_PRINTED_TOTAL_BY_EXPANSION[expansion.id.lowercase()]
                ?: expansion.officialCount?.takeIf { it > 0 }
                ?: expansion.cardCount
            val logo = expansion.logoUrl(WORKER_BASE_URL)
            return TcgSet(
                id = italianSetId(expansion.id),
                name = expansion.name,
                printedTotal = total,
                series = expansion.series?.takeIf { it.isNotBlank() }?.let(ItalianTranslations::translateSeriesName).orEmpty(),
                language = "ITA",
                total = total,
                releaseDate = expansion.releaseDate.orEmpty(),
                images = SetImages(symbol = logo, logo = logo),
            )
        }

        /** toItalianTcgCard di Android, con i prezzi del Worker al posto della carta inglese. */
        fun toItalianTcgCard(card: Card, setInfo: TcgSet, price: PriceEntry?): TcgCard {
            fun image(size: String) = card.imageUrl(WORKER_BASE_URL + "/", size = size)?.let { "$it&itv=r2v3" }.orEmpty()
            val hasEur = price != null && (price.avg != null || price.low != null || price.trend != null)
            return TcgCard(
                id = card.italianId() ?: card.cardId,
                name = card.nome,
                number = card.number.orEmpty(),
                images = CardImages(small = image("low"), large = image("high")),
                set = setInfo,
                rarity = card.rarity?.takeIf { it.isNotBlank() },
                source = card,
                supertype = ItalianCardFacets.supertypeOf(card),
                hp = card.ps?.takeIf { it.isNotBlank() },
                subtypes = card.stage?.trim()?.takeIf { it.isNotBlank() }?.let { listOf(it) } ?: emptyList(),
                types = card.tipo?.takeIf { it.isNotBlank() }?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.takeIf { it.isNotEmpty() },
                // CardOptions.USD_ONLY_PRICE_KEY, come SetDetailViewModel.withPriceData su Android.
                tcgplayer = if (price != null && (price.usd != null || price.usdLow != null)) {
                    TcgPlayer(
                        url = price.url.takeIf { !hasEur }.orEmpty(),
                        prices = mapOf(CardOptions.USD_ONLY_PRICE_KEY to TcgPriceInfo(low = price.usdLow, market = price.usd)),
                    )
                } else null,
                cardmarket = price?.let {
                    CardMarket(
                        url = it.url.takeIf { hasEur }.orEmpty(),
                        prices = CardMarketPrices(
                            averageSellPrice = it.avg,
                            lowPrice = it.low,
                            trendPrice = it.trend,
                            avg1 = it.avg1,
                            avg7 = it.avg7,
                            avg30 = it.avg30,
                        ),
                    )
                },
                artist = card.illustratore?.takeIf { it.isNotBlank() },
            )
        }
    }
}
