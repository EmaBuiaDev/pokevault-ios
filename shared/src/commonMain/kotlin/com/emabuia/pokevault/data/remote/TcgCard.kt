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
)

data class TcgImages(val small: String)

data class TcgSet(val id: String, val name: String)
