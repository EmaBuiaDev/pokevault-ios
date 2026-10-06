package com.emabuia.pokevault.data

import com.emabuia.pokevault.data.remote.PokemonNameMatcher
import com.emabuia.pokevault.data.remote.SetCodeMapper
import com.emabuia.pokevault.util.IllustratorNames

/**
 * La carta del catalogo italiano che una riga di decklist indica:
 * findExactItalianCard di CatalogRepository su Android, sugli stessi dati
 * (il catalogo completo che l'app tiene sul telefono).
 */
object ItalianCardLookup {

    private val NON_ALNUM = Regex("[^a-z0-9]+")

    /**
     * Set e numero, e il nome come conferma. Sui Pokemon il nome intero non
     * basta come veto (forme e possessivi si traducono: "Lillie's Clefairy ex"
     * e' "Clefairy-ex di Lylia"): si accetta la carta se la specie e' la
     * stessa, che e' quanto serve a scartare un Treecko al posto di un Kadabra.
     */
    fun findExact(
        catalog: List<Card>,
        setCode: String?,
        number: String?,
        expectedName: String? = null,
        requireNameMatch: Boolean = false,
    ): Card? {
        val normalizedNumber = number?.trim()?.substringBefore('/')?.trim()?.trimStart('0')?.ifBlank { "0" }
            ?: return null
        val normalizedTargetSet = setCode
            ?.let(SetCodeMapper::normalizeDecklistSetCode)
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?: return null

        val wantedName = expectedName?.let(::normalizeCardNameForComparison)?.takeIf { it.isNotBlank() }

        // Una passata sola, che si ferma appena trova la carta giusta.
        var fallback: Card? = null
        var sameSpecies: Card? = null
        var chosen: Card? = null

        for (rec in catalog) {
            if (!matchesItalianExpansionHint(rec.espansioneId.trim().lowercase(), normalizedTargetSet)) continue
            val cardNum = (rec.number ?: continue).trimStart('0').ifBlank { "0" }
            if (!cardNum.equals(normalizedNumber, ignoreCase = true)) continue

            if (wantedName == null) {
                chosen = rec
                break
            }

            val found = normalizeCardNameForComparison(rec.nome)
            if (found == wantedName || found.startsWith(wantedName) || wantedName.startsWith(found)) {
                chosen = rec
                break
            }
            if (fallback == null) fallback = rec
            if (sameSpecies == null && PokemonNameMatcher.sameSpecies(expectedName, rec.nome)) sameSpecies = rec
        }

        return chosen
            ?: sameSpecies
            ?: if (requireNameMatch) null else fallback
    }

    fun normalizeCardNameForComparison(raw: String?): String =
        IllustratorNames.stripDiacritics(raw.orEmpty().lowercase()).replace(NON_ALNUM, "")

    internal fun matchesItalianExpansionHint(expansionId: String, expectedSetId: String): Boolean {
        val candidates = linkedSetOf<String>()
        preferredBaseSetCodeForItalianExpansion(expansionId)?.let { code ->
            SetCodeMapper.normalizeDecklistSetCode(code)?.lowercase()?.let(candidates::add)
        }
        SetCodeMapper.normalizeDecklistSetCode(expansionId)?.lowercase()?.let(candidates::add)
        return expectedSetId in candidates
    }

    /** La sigla inglese delle espansioni italiane: la stessa tabella di Android (BLK/WHT compresi). */
    private fun preferredBaseSetCodeForItalianExpansion(expansionId: String): String? =
        when (expansionId.trim().lowercase()) {
            "me01" -> "MEG"
            "me02" -> "PFL"
            "me03" -> "ME03"
            "me04" -> "CRI"
            "me2pt5" -> "ASC"
            "mep" -> "MEP"
            "sv01" -> "SVI"
            "sv02" -> "PAL"
            "sv03" -> "OBF"
            "sv04" -> "PAR"
            "sv05" -> "TEF"
            "sv06" -> "TWM"
            "sv07" -> "SCR"
            "sv08" -> "SSP"
            "sv09" -> "JTG"
            "sv10" -> "DRI"
            // zsv10pt5 (Zekrom-ex) e' Luce Nera, BLK; rsv10pt5 (Reshiram-ex) e' Fuoco Bianco, WHT.
            "zsv10pt5" -> "BLK"
            "rsv10pt5" -> "WHT"
            "sv3pt5" -> "MEW"
            "sv4pt5" -> "PAF"
            "sv6pt5" -> "SFA"
            "sv8pt5" -> "PRE"
            else -> null
        }
}
