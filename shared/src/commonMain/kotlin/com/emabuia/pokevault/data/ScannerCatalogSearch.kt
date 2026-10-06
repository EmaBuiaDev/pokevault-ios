package com.emabuia.pokevault.data

import com.emabuia.pokevault.data.remote.SetCodeMapper
import com.emabuia.pokevault.util.IllustratorNames
import kotlin.math.abs

/**
 * Le carte del catalogo italiano che lo Scanner propone per una lettura:
 * searchItalianScannerCandidates di CatalogRepository su Android.
 *
 * Il numero (e il set, se letto) filtrano; il nome e il totale stampato del
 * set ordinano. Il nome OCR e' spesso sporco: se il filtro sul nome lascia
 * niente ma il numero c'e', si riprova senza.
 */
object ScannerCatalogSearch {

    data class Hit(val card: Card, val printedTotal: Int?)

    /** Totale stampato corretto a mano dove il dato ereditato e' sbagliato (Android). */
    private val PRINTED_TOTAL_OVERRIDES = mapOf("me04" to 87)

    private const val MIN_NAME_SCORE = 36

    fun search(
        catalog: List<Card>,
        expansions: List<Expansion>,
        name: String?,
        number: String?,
        setTotal: String?,
        targetSetId: String?,
        limit: Int = 6,
    ): List<Hit> {
        val normalizedNumber = number?.trim()?.trimStart('0')?.ifBlank { "0" }
        val normalizedName = normalizeNameForLookup(name)
        val normalizedTargetSet = targetSetId
            ?.let(SetCodeMapper::normalizeDecklistSetCode)
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
        val targetTotal = setTotal?.toIntOrNull()

        if (normalizedNumber.isNullOrBlank() && normalizedName.isBlank()) return emptyList()

        val expansionsById = expansions.associateBy { it.id.trim().lowercase() }

        val baseCandidates = catalog.filter { record ->
            val numberOk = normalizedNumber.isNullOrBlank() ||
                (record.number ?: "").trimStart('0').ifBlank { "0" } == normalizedNumber
            numberOk && (
                normalizedTargetSet == null ||
                    ItalianCardLookup.matchesItalianExpansionHint(record.espansioneId.trim().lowercase(), normalizedTargetSet)
                )
        }

        fun printedTotalOf(expansionId: String): Int? =
            PRINTED_TOTAL_OVERRIDES[expansionId]
                ?: expansionsById[expansionId]?.officialCount?.takeIf { it > 0 }
                ?: expansionsById[expansionId]?.cardCount?.takeIf { it > 0 }

        fun rank(applyNameGate: Boolean): List<Pair<Card, Int>> =
            baseCandidates.mapNotNull { record ->
                val nameScore = scannerNameScore(normalizeNameForLookup(record.nome), normalizedName)
                if (applyNameGate && normalizedName.isNotBlank() && nameScore < MIN_NAME_SCORE) return@mapNotNull null

                // Numero e set sono gia' filtri: discriminano solo nome e totale.
                var score = nameScore
                val printedTotal = printedTotalOf(record.espansioneId.trim().lowercase())
                if (targetTotal != null && printedTotal != null) {
                    score += when {
                        printedTotal == targetTotal -> 90
                        abs(printedTotal - targetTotal) <= 2 -> 25
                        // Totale incompatibile: un'altra espansione, ma la cifra puo'
                        // essere letta male. Penalizza senza escludere.
                        else -> -45
                    }
                }
                record to score
            }.sortedWith(
                compareByDescending<Pair<Card, Int>> { it.second }
                    .thenBy { it.first.espansioneId }
                    .thenBy { it.first.number?.toIntOrNull() ?: Int.MAX_VALUE }
            ).take(limit.coerceIn(1, 20))

        val gated = rank(applyNameGate = true)
        val ranked = if (gated.isEmpty() && normalizedName.isNotBlank() && !normalizedNumber.isNullOrBlank()) {
            rank(applyNameGate = false)
        } else {
            gated
        }

        return ranked
            .map { (record, _) -> Hit(record, printedTotalOf(record.espansioneId.trim().lowercase())) }
            .distinctBy { it.card.italianId() ?: it.card.cardId }
    }

    private val CAMEL = Regex("([a-z])([A-Z])")
    private val NON_ALNUM = Regex("[^a-z0-9]+")
    private val SPACES = Regex("\\s+")

    internal fun normalizeNameForLookup(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val deCamel = raw.replace(CAMEL, "$1 $2")
        return IllustratorNames.stripDiacritics(deCamel)
            .lowercase()
            .replace(NON_ALNUM, " ")
            .trim()
            .replace(SPACES, " ")
    }

    internal fun scannerNameScore(normalizedName: String, normalizedQuery: String): Int {
        if (normalizedQuery.isBlank()) return 0
        if (normalizedName == normalizedQuery) return 120
        if (normalizedName.startsWith(normalizedQuery) || normalizedQuery.startsWith(normalizedName)) return 105
        if (normalizedName.contains(normalizedQuery) || normalizedQuery.contains(normalizedName)) return 88

        val queryTokens = normalizedQuery.split(" ").filter { it.length >= 2 }
        val nameTokens = normalizedName.split(" ").filter { it.length >= 2 }
        if (queryTokens.isNotEmpty() && queryTokens.all { token -> nameTokens.any { it.contains(token) || token.contains(it) } }) {
            return 72
        }

        val distance = levenshtein(normalizedName, normalizedQuery)
        val maxLength = maxOf(normalizedName.length, normalizedQuery.length).coerceAtLeast(1)
        val similarity = 1.0 - distance.toDouble() / maxLength.toDouble()
        return when {
            similarity >= 0.82 -> 68
            similarity >= 0.72 -> 52
            similarity >= 0.62 -> 36
            else -> 0
        }
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
