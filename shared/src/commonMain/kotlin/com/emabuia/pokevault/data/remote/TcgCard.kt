package com.emabuia.pokevault.data.remote

import com.emabuia.pokevault.data.Card

/**
 * Una carta del catalogo nella forma TcgCard dell'app Android
 * (data/remote/PokeTcgApiService.kt): la usano l'editor dei deck, lo Scanner
 * e il Pokedex portati da li'. Su iOS il catalogo e' solo quello italiano:
 * [source] e' la carta del catalogo, da cui si scrive il documento in
 * collezione. I campi in piu' hanno un default, cosi' chi ne usa pochi non
 * deve passarli.
 */
data class TcgCard(
    val id: String = "",
    val name: String = "",
    val number: String = "",
    val images: TcgImages = TcgImages(""),
    val set: TcgSet? = null,
    val rarity: String? = null,
    // Vuota solo nei test: una carta senza id del catalogo non si scrive (CollectionWriter lo rifiuta).
    val source: Card = Card(cardId = ""),
    /** Pokémon, Trainer o Energy, come lo ricava il catalogo: serve al matcher dello Scanner. */
    val supertype: String = "",
    val hp: String? = null,
    val subtypes: List<String>? = null,
    val types: List<String>? = null,
    val tcgplayer: TcgPlayer? = null,
    /** I prezzi Cardmarket: dal Worker (/ita/prices), con le medie a 1/7/30 giorni. */
    val cardmarket: CardMarket? = null,
    /** L'illustratore (ItalianCardRecord.illustratore): un nome di persona, non si traduce. */
    val artist: String? = null,
)

data class TcgImages(val small: String, val large: String = "")

/** CardImages di Android. */
typealias CardImages = TcgImages

/**
 * Un'espansione. [printedTotal]: il numero dopo la barra ("066/217"), 0 se
 * non si sa; [total]: le carte nel catalogo, segrete comprese.
 */
data class TcgSet(
    val id: String,
    val name: String,
    val printedTotal: Int = 0,
    val series: String = "",
    val language: String? = null,
    val total: Int = 0,
    /** "yyyy-MM-dd" come la manda il Worker; vuota se non si sa. */
    val releaseDate: String = "",
    val images: SetImages = SetImages(),
)

/** Il set dentro una carta: su Android e' una classe a parte con un sottoinsieme dei campi. */
typealias TcgCardSet = TcgSet

data class SetImages(val symbol: String = "", val logo: String = "")

data class TcgPlayer(
    val url: String = "",
    val prices: Map<String, TcgPriceInfo>? = null,
)

data class TcgPriceInfo(
    val low: Double? = null,
    val mid: Double? = null,
    val high: Double? = null,
    val market: Double? = null,
)

data class CardMarket(
    val url: String = "",
    val prices: CardMarketPrices? = null,
)

data class CardMarketPrices(
    val averageSellPrice: Double? = null,
    val lowPrice: Double? = null,
    val trendPrice: Double? = null,
    val lowPriceExPlus: Double? = null,
    val suggestedPrice: Double? = null,
    val avg1: Double? = null,
    val avg7: Double? = null,
    val avg30: Double? = null,
    val reverseHoloLow: Double? = null,
    val reverseHoloTrend: Double? = null,
)
