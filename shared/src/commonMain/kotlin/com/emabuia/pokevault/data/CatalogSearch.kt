package com.emabuia.pokevault.data

/**
 * Ricerca per nome nel catalogo italiano completo, in memoria: la stessa di
 * CatalogRepository.searchItalianCardsByName nell'app Android, con lo stesso
 * punteggio, cosi' i risultati escono nello stesso ordine.
 */
object CatalogSearch {
    private val MULTI_SPACE = Regex("\\s+")
    private val CAMEL = Regex("([a-z])([A-Z])")
    private val NON_ALNUM = Regex("[^a-z0-9]+")

    // Al posto di java.text.Normalizer (solo JVM): le lettere accentate che
    // compaiono nei nomi delle carte, ricondotte alla lettera semplice.
    private val FOLD: Map<Char, Char> = buildMap {
        "àáâäãå".forEach { put(it, 'a') }
        "èéêë".forEach { put(it, 'e') }
        "ìíîï".forEach { put(it, 'i') }
        "òóôöõø".forEach { put(it, 'o') }
        "ùúûü".forEach { put(it, 'u') }
        put('ç', 'c'); put('ñ', 'n'); put('ý', 'y'); put('ÿ', 'y')
    }

    fun sanitizeQuery(raw: String): String = raw.replace(MULTI_SPACE, " ").trim()

    fun normalizeNameForLookup(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val folded = raw.replace(CAMEL, "$1 $2")
            .lowercase()
            .map { FOLD[it] ?: it }
            .joinToString("")
        return sanitizeQuery(folded.replace(NON_ALNUM, " ").trim())
    }

    fun scoreItalianNameMatch(normalizedName: String, normalizedQuery: String, queryTokens: List<String>): Int {
        if (normalizedName == normalizedQuery) return 1000

        val startsWith = normalizedName.startsWith(normalizedQuery)
        val wordContains = " $normalizedName ".contains(" $normalizedQuery ")

        if (startsWith) return 850
        if (wordContains) return 700
        if (normalizedName.contains(normalizedQuery)) return 560

        val longTokens = queryTokens.filter { it.length >= 2 }
        if (longTokens.isNotEmpty() && longTokens.all { token -> normalizedName.contains(token) }) {
            return 420
        }
        if (longTokens.any { token -> token.length >= 3 && " $normalizedName ".contains(" $token ") }) {
            return 260
        }
        return 0
    }

    /** Le carte che somigliano a [query], dalla piu' simile; a parita', per numero. */
    fun search(cards: List<Card>, query: String, limit: Int = 300): List<Card> {
        val normalizedQuery = normalizeNameForLookup(sanitizeQuery(query))
        if (normalizedQuery.isBlank()) return emptyList()
        val queryTokens = normalizedQuery.split(" ").filter { it.isNotBlank() }
        return cards.asSequence()
            .mapNotNull { card ->
                val name = normalizeNameForLookup(card.nome)
                if (name.isBlank()) return@mapNotNull null
                val score = scoreItalianNameMatch(name, normalizedQuery, queryTokens)
                if (score <= 0) null else card to score
            }
            .sortedWith(
                compareByDescending<Pair<Card, Int>> { it.second }
                    .thenBy { it.first.number?.toIntOrNull() ?: Int.MAX_VALUE }
                    .thenBy { it.first.espansioneId }
            )
            .take(limit.coerceIn(1, 300))
            .map { it.first }
            .toList()
    }
}
