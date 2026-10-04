package com.emabuia.pokevault.data

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CardTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun numberAndImageFromCardId() {
        val card = Card(cardId = "ME05_IT_001.webp")
        assertEquals("1", card.number)
        assertEquals("https://x.dev/images/it/ME05/1?size=low", card.imageUrl("https://x.dev/"))

        // Le gallerie tengono il numero com'e', zeri compresi: e' anche la chiave del prezzo.
        val gallery = Card(cardId = "SWSH12PT5GG_IT_GG01.png")
        assertEquals("GG01", gallery.number)
        assertEquals("https://x.dev/images/it/SWSH12PT5GG/GG01?size=high", gallery.imageUrl("https://x.dev", "high"))

        assertNull(Card(cardId = "senza-formato").imageUrl("https://x.dev"))
    }

    @Test
    fun sortsNumericallyThenGalleries() {
        val ids = listOf("X_IT_TG02.png", "X_IT_10.png", "X_IT_2.png", "X_IT_TG01.png", "X_IT_1.png")
        val sorted = ids.map { Card(cardId = it) }.sortedWith(Card.byNumber).map { it.number }
        assertEquals(listOf("1", "2", "10", "TG01", "TG02"), sorted)
    }

    @Test
    fun displayPriceIsTheLowInEuro() {
        assertEquals("€ 0,99", PriceEntry(avg = 3.3, low = 0.99, trend = 3.0, usd = 2.85).displayText())
        assertEquals("€ 3,30", PriceEntry(avg = 3.3, low = null).displayText())
        assertEquals("€ 12,50", PriceEntry(low = 12.5).displayText())
        // sma e bwp hanno solo TCGPlayer.
        assertEquals("$ 1,99", PriceEntry(usd = 2.85, usdLow = 1.99).displayText())
        assertNull(PriceEntry(low = 0.0).displayText())
    }

    @Test
    fun parsesWorkerPayloads() {
        val cards = json.decodeFromString<CardsResponse>(
            """{"expansionId":"me05","cards":[{"cardId":"ME05_IT_001.webp","espansioneId":"me05","nome":"Tropius",
               "attacchi":[{"nome":"Aroma Fruttato","danno":"","descrizione":"..."}],"regolaSpeciale":null,
               "rarity":"Common","stage":"Basic","illustratore":"Akino Fukuji"}]}"""
        ).cards
        assertEquals("Tropius", cards.single().nome)
        assertEquals("Akino Fukuji", cards.single().illustratore)

        val prices = json.decodeFromString<ExpansionPrices>(
            """{"expansionId":"me05","baseSetCode":"PBL","updatedAt":1,"builtAt":2,
               "prices":{"1":{"avg":0.05,"low":0.02,"trend":0.02,"avg1":0.02,"url":"https://www.cardmarket.com/x"}}}"""
        ).prices
        assertEquals("€ 0,02", prices["1"]?.displayText())
    }
}
