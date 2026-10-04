package com.emabuia.pokevault.util

/**
 * Il pezzo di AppLocale dell'app Android che serve qui, con gli stessi nomi:
 * il codice portato da Android lo chiama senza modifiche.
 *
 * Per ora l'app iOS e' solo in italiano. Le tabelle sono copiate
 * dall'originale; quando si aggiunge l'inglese, si porta il resto del file.
 */
object AppLocale {
    val isItalian: Boolean get() = true

    val errorPrefix: String get() = "Errore"
    val unknownExpansion: String get() = "Espansione sconosciuta"
    val unknown: String get() = "Sconosciuto"
    val other: String get() = "Altro"
    val unknownError: String get() = "Errore sconosciuto"
    val back: String get() = "Indietro"
    val wishlistTitle: String get() = "Wishlist"
    val wishlistIconPokeBall: String get() = "Da prendere"
    val wishlistIconGreatBall: String get() = "Priorità"
    val wishlistIconUltraBall: String get() = "Costose"
    val wishlistIconMasterBall: String get() = "Carta della vita"
    val wishlistIconBudget: String get() = "Occasioni"
    val wishlistIconTrade: String get() = "Da scambiare"
    val wishlistIconGift: String get() = "Regalo"
    val wishlistIconGraded: String get() = "Da gradare"
    val wishlistIconDeck: String get() = "Per il deck"
    val wishlistIconSet: String get() = "Completa set"

    val printLabel: String get() = "Stampa"
    val quantity: String get() = "Quantità"
    val condition: String get() = "Condizione"
    val languageLabel: String get() = "Lingua"
    val addToCollection: String get() = "Aggiungi alla collezione"
    val addCopy: String get() = "Aggiungi copia"
    fun addCopies(quantity: Int): String = "Aggiungi $quantity copie"
    val removeFromCollection: String get() = "Rimuovi"
    val alreadyOwnedPrint: String get() = "Già in collezione"

    val search: String get() = "Cerca..."
    val searchCard: String get() = "Cerca una carta"
    val searchInSets: String get() = "Cerca tra tutte le espansioni..."
    val offlineMessage: String get() = "Sei offline. Alcune funzioni non sono disponibili."

    val statistics: String get() = "Statistiche"
    val totalCards: String get() = "Carte Totali"
    val uniqueCards: String get() = "Carte Uniche"
    val totalValue: String get() = "Valore Totale"
    val averageValue: String get() = "Valore Medio"
    val mostValuable: String get() = "Più Preziosa"
    val graded: String get() = "Graduate"
    val setCompletion: String get() = "Completamento Set"
    val bySet: String get() = "Per Set"
    val byRarity: String get() = "Per Rarità"
    val byType: String get() = "Per Tipo"
    val emptyStatsTitle: String get() = "Nessuna statistica disponibile"
    val emptyStatsSubtitle: String get() = "Aggiungi carte alla tua collezione per vedere le statistiche"
    fun setCompletedTitle(setName: String) = "$setName completato"
    val setCompletedSubtitle: String get() = "Hai tutte le carte di questa espansione"

    private val rarityItMap = mapOf(
        "common" to "Comune",
        "uncommon" to "Non Comune",
        "rare" to "Rara",
        "rare holo" to "Rara Holo",
        "holo rare" to "Rara Holo",
        "ultra rare" to "Ultra Rara",
        "rare ultra" to "Ultra Rara",
        "secret rare" to "Rara Segreta",
        "rare secret" to "Rara Segreta",
        "amazing rare" to "Rara Fantastica",
        "rare holo ex" to "Rara Holo EX",
        "rare holo v" to "Rara Holo V",
        "rare holo gx" to "Rara Holo GX",
        "rare holo vmax" to "Rara Holo VMAX",
        "rare holo vstar" to "Rara Holo VSTAR",
        "double rare" to "Doppia Rara",
        "illustration rare" to "Illustrazione Rara",
        "special illustration rare" to "Illustrazione Rara Speciale",
        "futuristic rare" to "Rara Futuristica",
        "hyper rare" to "Iper Rara",
        "shiny rare" to "Rara Shiny",
        "shiny ultra rare" to "Ultra Rara Shiny",
        "rainbow rare" to "Rara Arcobaleno",
        "gold rare" to "Rara Oro",
        "full art" to "Full Art",
        "alt art" to "Arte Alternativa",
        "ace spec rare" to "ACE SPEC Rara",
        "promo" to "Promo",
        "radiant rare" to "Rara Radiante",
        "rare prime" to "Rara Prime",
        "legend" to "LEGEND",
        "black white rare" to "Rara B/W",
        // Le forme con le parole invertite ("Holo Rare V" da TCGdex contro
        // "Rare Holo V" da PokeWallet) vivono tutte e due nel catalogo, e
        // senza la loro riga qui il chip del filtro resta in inglese in meta'
        // dei set SWSH.
        "holo rare v" to "Rara Holo V",
        "holo rare vmax" to "Rara Holo VMAX",
        "holo rare vstar" to "Rara Holo VSTAR",
        "shiny rare v" to "Rara Shiny V",
        "shiny rare vmax" to "Rara Shiny VMAX",
        "rare holo lv.x" to "Rara Holo LV.X",
        "mega hyper rare" to "Mega Iper Rara",
        "full art trainer" to "Allenatore Full Art",
        "classic collection" to "Collezione Classica",
        "pikachu rare" to "Rara Pikachu",
        // Le carte dei mazzi introduttivi la rarita' stampata non ce l'hanno.
        "none" to "Nessuna"
    )

    fun translateRarity(rarity: String): String {
        if (rarity.isBlank()) return rarity
        val key = rarity.lowercase().trim()
        return rarityItMap[key] ?: rarity
    }

    private val typeEnToIt = mapOf(
        "fire" to "Fuoco",
        "water" to "Acqua",
        "grass" to "Erba",
        "lightning" to "Elettro",
        "psychic" to "Psico",
        "fighting" to "Lotta",
        "darkness" to "Buio",
        "metal" to "Metallo",
        "dragon" to "Drago",
        "fairy" to "Folletto",
        "colorless" to "Incolore",
        "normal" to "Normale"
    )

    // Oltre ai nomi della tabella, quelli che il catalogo italiano usa davvero
    // (contati il 04/10/2026 su /ita/catalog.json): Lampo 1154 carte,
    // Oscurità 1078, Combattimento 147, Psiche 137. Senza, quelle carte
    // restavano senza colore del tipo.
    private val typeItToEn = typeEnToIt.entries.associate { (k, v) -> v.lowercase() to k.replaceFirstChar { it.uppercase() } } +
        mapOf("lampo" to "Lightning", "oscurità" to "Darkness", "combattimento" to "Fighting", "psiche" to "Psychic")

    /** Normalizza un tipo (italiano o inglese) alla versione inglese: serve a TypeColors. */
    fun typeToEnglish(type: String): String {
        if (type.isBlank()) return type
        val key = type.lowercase().trim()
        if (typeEnToIt.containsKey(key)) return key.replaceFirstChar { it.uppercase() }
        return typeItToEn[key] ?: type
    }

    fun translateType(type: String): String {
        if (type.isBlank()) return type
        val key = type.lowercase().trim()
        return typeEnToIt[key] ?: type
    }

    fun displaySetName(setName: String): String {
        if (setName.isBlank()) return setName
        return com.emabuia.pokevault.data.local.ItalianTranslations.translateExpansionName(setName)
    }
}
