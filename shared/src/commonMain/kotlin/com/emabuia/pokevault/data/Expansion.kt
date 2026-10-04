package com.emabuia.pokevault.data

import kotlinx.serialization.Serializable

/**
 * Una voce di `GET /v1/expansions`: lo stesso manifest leggero che usa l'app
 * Android (ItalianExpansionSummary), senza le carte.
 */
@Serializable
data class Expansion(
    val id: String,
    val name: String,
    val cardCount: Int = 0,
    // Il numero stampato dopo la barra ("066/217"); null per i promo.
    val officialCount: Int? = null,
    val sortOrder: Int = 0,
    val baseSetCode: String? = null,
    val releaseDate: String? = null,
    val series: String? = null,
) {
    // Cartella su R2: il codice del set in maiuscolo.
    val folderName: String get() = (baseSetCode ?: id).uppercase()

    // source=ita: senza, per i set senza logo italiano il Worker ripiega su
    // quello di PokeWallet, che spesso e' in un'altra lingua.
    fun logoUrl(baseUrl: String): String =
        "${baseUrl.trimEnd('/')}/sets/${LOGO_CODES[folderName] ?: folderName}/image?v=setimg-v5&source=ita"

    companion object {
        // Per questi set il logo sta sotto il codice stampato sulle carte, non
        // sotto l'id: con l'id il Worker risponde 404 (verificato il 04/10/2026
        // su tutte le 136 espansioni). E' l'inverso di SetCodeMapper nell'app
        // Android: un set nuovo con lo stesso problema va aggiunto qui.
        private val LOGO_CODES = mapOf(
            "SV01" to "SVI", "SV02" to "PAL", "SV03" to "OBF", "SV3PT5" to "MEW",
            "SV04" to "PAR", "SV4PT5" to "PAF", "SV05" to "TEF", "SV06" to "TWM",
            "SV6PT5" to "SFA", "SV07" to "SCR", "SV08" to "SSP", "SV8PT5" to "PRE",
            "SV09" to "JTG", "SV10" to "DRI",
            "ZSV10PT5" to "BLK", "RSV10PT5" to "WHT",
            "ME01" to "MEG", "ME02" to "PFL", "ME2PT5" to "ASC", "ME04" to "CRI",
        )
    }
}

@Serializable
data class ExpansionsResponse(
    val expansions: List<Expansion> = emptyList(),
)
