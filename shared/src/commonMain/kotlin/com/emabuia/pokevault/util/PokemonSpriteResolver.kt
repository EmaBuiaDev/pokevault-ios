package com.emabuia.pokevault.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.emabuia.pokevault.resources.Res
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dal nome di una carta, o di un archetipo scritto a mano, allo sprite del
 * Pokemon: util/PokemonSpriteResolver.kt di Android.
 *
 * La tabella numero -> specie e' la stessa (`files/pokemon_species.txt` nelle
 * risorse, copiata dagli asset Android). Cambia solo come si carica e come si
 * tolgono gli accenti, perche' su iOS non c'e' java.text.Normalizer.
 *
 * Come su Android, lo sprite e' della specie e non della stampa: Charizard ex
 * e Charizard V hanno lo stesso.
 */
object PokemonSpriteResolver {

    private const val RESOURCE = "files/pokemon_species.txt"

    /**
     * Sprite statici e non le GIF animate: quelle esistono solo fino al 649.
     * Lo stesso indirizzo di Android, perche' le copertine dei mazzi salvate
     * da Android sono questi URL e [isSpriteUrl] li riconosce dal prefisso.
     */
    private const val SPRITE_BASE =
        "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon"

    /** Pezzi che una carta aggiunge al nome della specie ("Charizard ex"). */
    private val SUFFIXES = setOf(
        "ex", "gx", "v", "vmax", "vstar", "vunion", "break", "prime",
        "legend", "star", "lvx", "delta"
    )

    /**
     * I Paradosso sono le sole specie che l'italiano traduce: la tabella di
     * PokeAPI non conoscerebbe mai "Furiatonante". PokemonNameMatcher.translatedSpecies
     * su Android.
     */
    internal val translatedSpecies: Map<String, String> = mapOf(
        "great tusk" to "grandizanne",
        "scream tail" to "codaurlante",
        "brute bonnet" to "fungofurioso",
        "flutter mane" to "crinealato",
        "slither wing" to "alirasenti",
        "sandy shocks" to "peldisabbia",
        "roaring moon" to "lunaruggente",
        "walking wake" to "acquecrespe",
        "gouging fire" to "vampeaguzze",
        "raging bolt" to "furiatonante",
        "iron treads" to "solcoferreo",
        "iron bundle" to "saccoferreo",
        "iron hands" to "manoferrea",
        "iron jugulis" to "colloferreo",
        "iron moth" to "falenaferrea",
        "iron thorns" to "spineferree",
        "iron valiant" to "eroeferreo",
        "iron leaves" to "fogliaferrea",
        "iron boulder" to "massoferreo",
        "iron crown" to "capoferreo"
    )

    private var speciesByKey: Map<String, Int> = emptyMap()

    /**
     * La tabella e' pronta. E' stato Compose: le righe che mostrano sprite lo
     * leggono e si ridisegnano quando la tabella arriva, invece di caricarla
     * dentro la propria composizione.
     */
    var isReady by mutableStateOf(false)
        private set

    /** Carica la tabella fuori dal thread principale. Chiamarla piu' volte non costa. */
    suspend fun preload() {
        if (isReady) return
        val table = try {
            withContext(Dispatchers.Default) { parse(Res.readBytes(RESOURCE).decodeToString()) }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // Una risorsa illeggibile non deve far saltare un elenco: si resta senza sprite.
            emptyMap()
        }
        install(table)
    }

    /** Per i test: la tabella gia' letta. */
    internal fun install(table: Map<String, Int>) {
        speciesByKey = table
        isReady = table.isNotEmpty()
    }

    /** Le righe `<numero>,<nome-specie>`, piu' i Paradosso in italiano. */
    internal fun parse(text: String): Map<String, Int> {
        val base = text.lineSequence().mapNotNull { line ->
            if (line.isBlank() || line.startsWith("#")) return@mapNotNull null
            val id = line.substringBefore(',').trim().toIntOrNull() ?: return@mapNotNull null
            val name = asciiKey(line.substringAfter(',').trim())
            if (name.isEmpty()) null else name to id
        }.toMap()
        return base + translatedSpecies.mapNotNull { (en, it) ->
            base[asciiKey(en)]?.let { id -> asciiKey(it) to id }
        }
    }

    private val NON_ALNUM = Regex("[^a-z0-9]+")

    /** Chiave per un nome gia' ASCII, come le righe della tabella. */
    private fun asciiKey(raw: String): String = buildString(raw.length) {
        for (ch in raw) {
            val c = ch.lowercaseChar()
            if (c in 'a'..'z' || c in '0'..'9') append(c)
        }
    }

    /** "Mr. Mime", "mr-mime" e "MrMime" diventano la stessa chiave; "Flabebe" trova "Flabébé". */
    private fun matchKey(raw: String): String =
        IllustratorNames.stripDiacritics(raw.lowercase()).replace(NON_ALNUM, "")

    private fun tokens(raw: String): List<String> =
        IllustratorNames.stripDiacritics(raw.lowercase())
            .replace(NON_ALNUM, " ")
            .trim()
            .split(' ')
            .filter { it.isNotEmpty() }

    /**
     * Il numero del Pokemon disegnato su questa carta, o null. Prova il nome
     * intero, poi leva i suffissi dalla coda ("Charizard ex"), poi i pezzi
     * dalla testa ("M Charizard", "Brock's Onix"), poi accorcia dalla coda
     * per i nomi italiani ("Clefairy-ex di Lylia").
     */
    fun dexNumberForCardName(cardName: String): Int? {
        if (cardName.isBlank()) return null
        val table = speciesByKey
        if (table.isEmpty()) return null

        table[matchKey(cardName)]?.let { return it }

        var parts = tokens(cardName)
        if (parts.isEmpty()) return null

        while (parts.size > 1 && parts.last() in SUFFIXES) {
            parts = parts.dropLast(1)
            table[matchKey(parts.joinToString(""))]?.let { return it }
        }

        while (parts.size > 1) {
            parts = parts.drop(1)
            table[matchKey(parts.joinToString(""))]?.let { return it }
        }

        val all = tokens(cardName)
        for (length in all.size - 1 downTo 1) {
            table[matchKey(all.take(length).joinToString(""))]?.let { return it }
        }

        return null
    }

    /** Questo indirizzo e' uno sprite, e non una vecchia copertina fatta con l'immagine di una carta. */
    fun isSpriteUrl(url: String): Boolean = url.startsWith(SPRITE_BASE)

    /**
     * Gli sprite dei Pokemon nominati nel nome di un archetipo ("Raging Bolt
     * Ogerpon"). Prova prima i nomi lunghi, fino a tre parole, perche' "Iron
     * Valiant" a una parola alla volta non si troverebbe; quello che non e'
     * un Pokemon ("ex", "Box") si salta.
     */
    fun spriteUrlsForArchetype(archetype: String, limit: Int = 2): List<String> {
        if (!isReady || archetype.isBlank() || limit <= 0) return emptyList()
        val table = speciesByKey
        if (table.isEmpty()) return emptyList()

        val parts = tokens(archetype)
        val found = LinkedHashSet<Int>()

        var i = 0
        while (i < parts.size && found.size < limit) {
            var consumed = 0
            for (window in minOf(3, parts.size - i) downTo 1) {
                val id = table[matchKey(parts.subList(i, i + window).joinToString(""))]
                if (id != null) {
                    found += id
                    consumed = window
                    break
                }
            }
            i += if (consumed > 0) consumed else 1
        }

        return found.map { "$SPRITE_BASE/$it.png" }
    }

    fun spriteUrlForCardName(cardName: String): String? {
        if (!isReady) return null
        val id = dexNumberForCardName(cardName) ?: return null
        return "$SPRITE_BASE/$id.png"
    }
}
