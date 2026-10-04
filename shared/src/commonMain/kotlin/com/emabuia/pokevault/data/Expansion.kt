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
    // Cartella su R2 e chiave del logo: il codice del set in maiuscolo.
    val folderName: String get() = (baseSetCode ?: id).uppercase()

    fun logoUrl(baseUrl: String): String = "${baseUrl.trimEnd('/')}/sets/$folderName/image"
}

@Serializable
data class ExpansionsResponse(
    val expansions: List<Expansion> = emptyList(),
)
