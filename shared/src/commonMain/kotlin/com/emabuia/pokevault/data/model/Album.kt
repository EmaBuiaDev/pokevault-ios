package com.emabuia.pokevault.data.model

import kotlinx.serialization.Serializable

/**
 * Un album del Collector Lab: data/model/Album.kt di Android, stessi campi su
 * users/{uid}/albums. [cardIds] sono gli id dei DOCUMENTI della collezione
 * (PokemonCard.id), non gli id del catalogo: nell'album entra la copia che hai.
 */
@Serializable
data class Album(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val pokemonType: String = "",       // Filtro per tipo Pokémon - vuoto = tutti
    val expansion: String = "",         // Filtro per espansione - vuoto = tutte
    val supertype: String = "",         // Filtro per categoria (Pokémon, Trainer, Energy) - vuoto = tutti
    val size: Int = 9,                  // Grandezza album (9, 18, 36, 72, 120)
    val theme: String = "classic",      // Tematica visuale
    val cardIds: List<String> = emptyList(),
    val coverImageUrl: String = "",
    val createdAt: Timestamp? = null
)

enum class GoalCriteriaType {
    SET,        // tutte le carte di un set specifico
    RARITY,     // tutte le carte di una rarità specifica
    SUPERTYPE,  // Pokémon / Trainer / Energy
    TYPE,       // Tipo Pokémon
    CUSTOM      // selezione manuale
}

/**
 * Un chase: data/model/GoalAlbum.kt di Android, su users/{uid}/goal_albums.
 * [targetCardApiIds] sono fissati alla creazione ("ita:me05:4"); il progresso
 * si calcola ogni volta contro la collezione e non si salva.
 */
@Serializable
data class GoalAlbum(
    val id: String = "",
    val name: String = "",
    // Stringa e non enum: un valore che questa versione non conosce non deve
    // far sparire il chase (su Android c'e' lo stesso ripiego su SET).
    val criteriaType: String = "SET",
    val criteriaValue: String = "",
    val targetCardApiIds: List<String> = emptyList(),
    val createdAt: Timestamp? = null
) {
    val criteria: GoalCriteriaType
        get() = GoalCriteriaType.entries.firstOrNull { it.name == criteriaType } ?: GoalCriteriaType.SET
}
