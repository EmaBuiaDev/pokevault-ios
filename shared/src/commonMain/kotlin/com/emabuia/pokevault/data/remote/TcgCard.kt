package com.emabuia.pokevault.data.remote

import com.emabuia.pokevault.data.Card

/**
 * Una carta trovata nel catalogo, nella forma che l'editor dei deck di Android
 * si aspetta (data/remote/TcgCard.kt li' ha molti piu' campi, qui servono
 * questi). Su iOS il catalogo e' solo quello italiano: [source] e' la carta
 * del catalogo, da cui si scrive il documento in collezione.
 */
data class TcgCard(
    val id: String,
    val name: String,
    val number: String,
    val images: TcgImages,
    val set: TcgSet?,
    val rarity: String?,
    val source: Card,
    /** Pokémon, Trainer o Energy, come lo ricava il catalogo: serve al matcher dello Scanner. */
    val supertype: String = "",
    val hp: String? = null,
    val subtypes: List<String>? = null,
    val types: List<String>? = null,
)

data class TcgImages(val small: String)

/** [printedTotal]: il numero dopo la barra ("066/217"), 0 se non si sa. */
data class TcgSet(val id: String, val name: String, val printedTotal: Int = 0)
