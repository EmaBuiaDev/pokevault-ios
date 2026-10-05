package com.emabuia.pokevault.util

/**
 * Nomi degli illustratori, conti e ordinamenti della sezione "Collezione per
 * illustratore": util/Illustrators.kt dell'app Android, con le stesse regole.
 *
 * Il catalogo porta i nomi **grezzi**, come li hanno scritti le due fonti che
 * lo hanno riempito (TCGdex in inglese, Pokemon Central Wiki per lo storico).
 * Il worker li raggruppa cosi' come sono e l'unificazione avviene qui.
 *
 * La chiave conta anche fuori dall'app: e' l'id del documento in
 * users/{uid}/followed_illustrators, quindi deve uscire identica a quella di
 * Android o una stella messa su un telefono non si vede sull'altro.
 */
object IllustratorNames {

    /** Grafie della stessa persona che la normalizzazione non unisce: vuota come su Android. */
    private val ALIASES: Map<String, String> = emptyMap()

    /** Solo "+" e "/" separano due autori; "&" e la virgola no (vedi Android). */
    private val CREDIT_SEPARATORS = Regex("""\s*[+/]\s*""")

    /** I ruoli davanti al nome ("Illus. Tizio", "Direc. Caio"). */
    private val ROLE_PREFIX = Regex("""^(illus|direc|dir|art)\.?\s+""", RegexOption.IGNORE_CASE)

    private val IGNORED_PUNCTUATION = Regex("""[.,'`’‘-]""")
    private val WHITESPACE = Regex("""\s+""")

    /**
     * Le lettere accentate dei blocchi latini e la loro base, come le lascia
     * Normalizer.Form.NFD togliendo i segni combinanti (\p{Mn}): il codice
     * comune non ha java.text.Normalizer. Generata da NFD su U+00C0..U+024F e
     * U+1E00..U+1EFF; un test sulla JVM la confronta col Normalizer vero.
     */
    private const val ACCENTED =
        "ÀÁÂÃÄÅÇÈÉÊËÌÍÎÏÑÒÓÔÕÖÙÚÛÜÝàáâãäåçèéêëìíîïñòóôõöùúûüýÿĀāĂăĄąĆ" +
        "ćĈĉĊċČčĎďĒēĔĕĖėĘęĚěĜĝĞğĠġĢģĤĥĨĩĪīĬĭĮįİĴĵĶķĹĺĻļĽľŃńŅņŇňŌōŎŏŐő" +
        "ŔŕŖŗŘřŚśŜŝŞşŠšŢţŤťŨũŪūŬŭŮůŰűŲųŴŵŶŷŸŹźŻżŽžƠơƯưǍǎǏǐǑǒǓǔǕǖǗǘǙǚǛ" +
        "ǜǞǟǠǡǢǣǦǧǨǩǪǫǬǭǮǯǰǴǵǸǹǺǻǼǽǾǿȀȁȂȃȄȅȆȇȈȉȊȋȌȍȎȏȐȑȒȓȔȕȖȗȘșȚțȞȟȦȧ" +
        "ȨȩȪȫȬȭȮȯȰȱȲȳḀḁḂḃḄḅḆḇḈḉḊḋḌḍḎḏḐḑḒḓḔḕḖḗḘḙḚḛḜḝḞḟḠḡḢḣḤḥḦḧḨḩḪḫḬḭḮḯ" +
        "ḰḱḲḳḴḵḶḷḸḹḺḻḼḽḾḿṀṁṂṃṄṅṆṇṈṉṊṋṌṍṎṏṐṑṒṓṔṕṖṗṘṙṚṛṜṝṞṟṠṡṢṣṤṥṦṧṨṩṪṫ" +
        "ṬṭṮṯṰṱṲṳṴṵṶṷṸṹṺṻṼṽṾṿẀẁẂẃẄẅẆẇẈẉẊẋẌẍẎẏẐẑẒẓẔẕẖẗẘẙẛẠạẢảẤấẦầẨẩẪẫẬ" +
        "ậẮắẰằẲẳẴẵẶặẸẹẺẻẼẽẾếỀềỂểỄễỆệỈỉỊịỌọỎỏỐốỒồỔổỖỗỘộỚớỜờỞởỠỡỢợỤụỦủỨ" +
        "ứỪừỬửỮữỰựỲỳỴỵỶỷỸỹ"
    private const val BASE =
        "AAAAAACEEEEIIIINOOOOOUUUUYaaaaaaceeeeiiiinooooouuuuyyAaAaAaC" +
        "cCcCcCcDdEeEeEeEeEeGgGgGgGgHhIiIiIiIiIJjKkLlLlLlNnNnNnOoOoOo" +
        "RrRrRrSsSsSsSsTtTtUuUuUuUuUuUuWwYyYZzZzZzOoUuAaIiOoUuUuUuUuU" +
        "uAaAaÆæGgKkOoOoƷʒjGgNnAaÆæØøAaAaEeEeIiIiOoOoRrRrUuUuSsTtHhAa" +
        "EeOoOoOoOoYyAaBbBbBbCcDdDdDdDdDdEeEeEeEeEeFfGgHhHhHhHhHhIiIi" +
        "KkKkKkLlLlLlLlMmMmMmNnNnNnNnOoOoOoOoPpPpRrRrRrRrSsSsSsSsSsTt" +
        "TtTtTtUuUuUuUuUuVvVvWwWwWwWwWwXxXxYyZzZzZzhtwyſAaAaAaAaAaAaA" +
        "aAaAaAaAaAaEeEeEeEeEeEeEeEeIiIiOoOoOoOoOoOoOoOoOoOoOoOoUuUuU" +
        "uUuUuUuUuYyYyYyYy"

    private val FOLD: Map<Char, Char> = ACCENTED.indices.associate { ACCENTED[it] to BASE[it] }

    /** Segni combinanti gia' separati (un testo arrivato in forma NFD). */
    private fun isCombiningMark(c: Char): Boolean =
        c in '̀'..'ͯ' || c in '᪰'..'᫿' || c in '᷀'..'᷿' ||
            c in '⃐'..'⃿' || c in '︠'..'︯'

    internal fun stripDiacritics(text: String): String = buildString(text.length) {
        for (c in text) {
            if (isCombiningMark(c)) continue
            append(FOLD[c] ?: c)
        }
    }

    /** I nomi da mostrare contenuti in un credito, separati e senza ruolo. */
    fun credits(raw: String?): List<String> {
        val clean = raw?.trim().orEmpty()
        if (clean.isEmpty()) return emptyList()
        val parts = clean.split(CREDIT_SEPARATORS)
        // Il ruolo si toglie solo dai pezzi nati da una separazione: "Illus. &
        // Direc. The Pokemon Company Art Team" e' un nome unico.
        val stripRoles = parts.size > 1
        return parts
            .map { part ->
                val named = if (stripRoles) part.replace(ROLE_PREFIX, "") else part
                named.replace(WHITESPACE, " ").trim()
            }
            .filter { it.isNotEmpty() }
    }

    /** Minuscolo, senza accenti, senza la punteggiatura che non distingue nessuno. */
    fun keyOf(name: String): String {
        val folded = stripDiacritics(name.trim())
            .lowercase()
            .replace(IGNORED_PUNCTUATION, "")
            .replace(WHITESPACE, " ")
            .trim()
        return ALIASES[folded] ?: folded
    }

    /** Le chiavi di un credito grezzo. Una carta a quattro mani conta per entrambi. */
    fun keysOf(raw: String?): List<String> =
        credits(raw).map(::keyOf).filter { it.isNotEmpty() }.distinct()

    /** Vince la grafia su piu' carte, a pari merito la prima in ordine alfabetico. */
    fun bestDisplayName(candidates: Map<String, Int>): String =
        candidates.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()?.key.orEmpty()
}

/**
 * Un illustratore e tutto cio' che lo riguarda nel catalogo, senza ancora
 * sapere niente della collezione. [rawNames] servono a richiedere le carte a
 * /v1/illustrators/{nome}/cards, che confronta il nome grezzo esatto.
 */
data class IllustratorEntry(
    val key: String,
    val displayName: String,
    val rawNames: List<String>,
    val cardApiIds: List<String>,
    val expansionCount: Int,
    /** Le prime tre carte, per le anteprime del Collector Lab. */
    val previewUrls: List<String> = emptyList(),
)

/** Una riga della lista illustratori, con l'avanzamento gia' calcolato. */
data class IllustratorRow(
    val key: String,
    val displayName: String,
    val owned: Int,
    val total: Int,
    val expansionCount: Int,
    val isFollowed: Boolean,
    val previewUrls: List<String> = emptyList(),
) {
    /** CollectorLab.fillPercent su Android. */
    val percent: Float get() = if (total <= 0) 0f else (owned.toFloat() / total.toFloat() * 100f).coerceIn(0f, 100f)
    val missing: Int get() = (total - owned).coerceAtLeast(0)
    val isComplete: Boolean get() = total > 0 && owned >= total
}

/** CLOSEST = "quasi fatti": i completati scendono in fondo. */
enum class IllustratorSort { CLOSEST, CARDS, NAME }

object Illustrators {

    /**
     * Raggruppa l'indice del Worker (nome grezzo -> chiavi immagine) per
     * persona, come italianIllustratorIndex su Android. [toApiId] trasforma
     * "DP1_IT_1.png" in "ita:dp1:1", null se la chiave non si legge.
     */
    fun entries(
        raw: List<Pair<String, List<String>>>,
        toApiId: (String) -> String?,
        toPreviewUrl: (String) -> String? = { null },
    ): List<IllustratorEntry> {
        class Bucket {
            val rawNames = linkedSetOf<String>()
            val displayNames = linkedMapOf<String, Int>()
            val cardIds = linkedSetOf<String>()
        }
        val buckets = LinkedHashMap<String, Bucket>()
        for ((rawName, cardIds) in raw) {
            // Un credito a quattro mani vale per entrambi: le stesse carte in due bucket.
            for (displayName in IllustratorNames.credits(rawName)) {
                val key = IllustratorNames.keyOf(displayName)
                if (key.isEmpty()) continue
                val bucket = buckets.getOrPut(key) { Bucket() }
                bucket.rawNames += rawName
                bucket.displayNames[displayName] = (bucket.displayNames[displayName] ?: 0) + cardIds.size
                bucket.cardIds += cardIds
            }
        }
        return buckets.map { (key, bucket) ->
            val apiIds = bucket.cardIds.mapNotNull(toApiId)
            IllustratorEntry(
                key = key,
                displayName = IllustratorNames.bestDisplayName(bucket.displayNames),
                rawNames = bucket.rawNames.toList(),
                cardApiIds = apiIds,
                // Dai cardId e non dall'expansionCount della rotta, che e' per
                // nome grezzo: sommarlo conterebbe due volte i set in comune.
                expansionCount = apiIds.map { it.split(':')[1] }.distinct().size,
                previewUrls = bucket.cardIds.take(3).mapNotNull(toPreviewUrl),
            )
        }
    }

    /** Incrocia il catalogo con la collezione: [ownedApiIds] e' un Set per i tanti contains. */
    fun rows(entries: List<IllustratorEntry>, ownedApiIds: Set<String>, followedKeys: Set<String>): List<IllustratorRow> =
        entries.map { entry ->
            IllustratorRow(
                key = entry.key,
                displayName = entry.displayName,
                owned = entry.cardApiIds.count { it in ownedApiIds },
                total = entry.cardApiIds.size,
                expansionCount = entry.expansionCount,
                isFollowed = entry.key in followedKeys,
                previewUrls = entry.previewUrls,
            )
        }

    fun sort(rows: List<IllustratorRow>, sort: IllustratorSort): List<IllustratorRow> = when (sort) {
        // Un artista completato non e' piu' un obiettivo: in fondo, come i chase.
        IllustratorSort.CLOSEST -> rows.sortedWith(
            compareBy<IllustratorRow> { it.isComplete }
                .thenByDescending { it.percent }
                .thenBy { it.missing }
                .thenBy { it.displayName.lowercase() }
        )
        IllustratorSort.CARDS -> rows.sortedWith(
            compareByDescending<IllustratorRow> { it.total }.thenBy { it.displayName.lowercase() }
        )
        IllustratorSort.NAME -> rows.sortedBy { it.displayName.lowercase() }
    }

    /** Sulla chiave normalizzata: "kohski" senza accenti trova chi ha l'accento. */
    fun filter(rows: List<IllustratorRow>, query: String): List<IllustratorRow> {
        val q = IllustratorNames.keyOf(query)
        if (q.isEmpty()) return rows
        return rows.filter { it.key.contains(q) }
    }

    /** I seguiti restano in cima, nell'ordine scelto; gli altri sotto. */
    fun partitionFollowed(rows: List<IllustratorRow>): Pair<List<IllustratorRow>, List<IllustratorRow>> =
        rows.partition { it.isFollowed }
}
