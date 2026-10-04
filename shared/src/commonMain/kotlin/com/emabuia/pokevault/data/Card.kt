package com.emabuia.pokevault.data

import kotlinx.serialization.Serializable
import kotlin.math.roundToLong

@Serializable
data class CardAttack(
    val nome: String = "",
    val danno: String = "",
    val descrizione: String = "",
)

/** Una carta di `GET /v1/expansions/{id}/cards` (ItalianCardRecord su Android). */
@Serializable
data class Card(
    val cardId: String,
    val espansioneId: String = "",
    val nome: String = "",
    val tipo: String? = null,
    val ps: String? = null,
    val attacchi: List<CardAttack> = emptyList(),
    val regolaSpeciale: String? = null,
    val rarity: String? = null,
    val stage: String? = null,
    // Il Worker omette la chiave quando e' vuota.
    val illustratore: String? = null,
) {
    private val match get() = CARD_ID.matchEntire(cardId.trim())

    // "ME05_IT_001.webp" -> cartella "ME05", numero "1"; "SWSH12TG_IT_TG01.png" -> "TG01".
    val folderName: String? get() = match?.groupValues?.get(1)?.uppercase()
    val number: String?
        get() = match?.groupValues?.get(2)?.let { raw -> raw.toIntOrNull()?.toString() ?: raw }

    /** L'id con cui l'app Android salva la carta nelle wishlist: "ita:me05:4". */
    fun italianId(): String? = folderName?.let { "ita:${it.lowercase()}:$number" }

    fun imageUrl(baseUrl: String, size: String = "low"): String? {
        val folder = folderName ?: return null
        return "${baseUrl.trimEnd('/')}/images/it/$folder/$number?size=$size"
    }

    companion object {
        // Stessa regola dell'app Android (ItalianCatalogNormalizer.imageIdRegex).
        private val CARD_ID = Regex("^([A-Za-z0-9-]+)_IT_([A-Za-z0-9_]+)\\.(png|webp|jpe?g)$", RegexOption.IGNORE_CASE)

        // Prima i numeri in ordine numerico (1, 2, 10), poi gallerie e simili (GG01, TG01).
        val byNumber: Comparator<Card> =
            compareBy<Card> { it.number?.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it.number.orEmpty() }
    }
}

@Serializable
data class CardsResponse(
    val expansionId: String = "",
    val cards: List<Card> = emptyList(),
)

@Serializable
data class PriceEntry(
    val avg: Double? = null,
    val low: Double? = null,
    val trend: Double? = null,
    // Ripiego TCGPlayer in dollari per i set senza Cardmarket (sma, bwp).
    val usd: Double? = null,
    val usdLow: Double? = null,
    val url: String? = null,
) {
    /**
     * Il prezzo da mostrare: sempre il minimo Cardmarket, come sull'app
     * Android (deciso `low`, non `trend`); la media solo se il minimo manca, e
     * i dollari solo se non c'e' nessun prezzo in euro.
     */
    fun displayText(): String? {
        val eur = low?.takeIf { it > 0 } ?: avg?.takeIf { it > 0 }
        if (eur != null) return "€ ${formatAmount(eur)}"
        val dollars = usdLow?.takeIf { it > 0 } ?: usd?.takeIf { it > 0 }
        return dollars?.let { "$ ${formatAmount(it)}" }
    }
}

/** `GET /ita/prices/{id}.json`: le chiavi sono i numeri di carta ("1", "GG01"). */
@Serializable
data class ExpansionPrices(
    val expansionId: String = "",
    val prices: Map<String, PriceEntry> = emptyMap(),
)

// Due decimali con la virgola, come String.format(Locale.ITALY, "%.2f") su Android.
internal fun formatAmount(value: Double): String {
    val cents = (value * 100).roundToLong()
    return "${cents / 100},${(cents % 100).toString().padStart(2, '0')}"
}
