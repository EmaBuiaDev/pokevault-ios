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
    val cancel: String get() = "Annulla"

    // Impostazioni: gli stessi testi dell'app Android.
    val settingsTitle: String get() = "Impostazioni"
    val themeLabel: String get() = "Tema"
    fun themeSubtitle(mode: String): String = when (mode) {
        "light" -> "Chiaro"
        "dark" -> "Scuro"
        else -> "Sistema"
    }
    val privacyPolicyLabel: String get() = "Informativa Privacy"
    val privacyPolicySubtitle: String get() = "Come gestiamo i tuoi dati"
    val privacyPolicyUrl: String get() = "https://emabuiadev.github.io/pokevault/privacy-policy"
    val termsLabel: String get() = "Termini di Servizio"
    val termsSubtitle: String get() = "Condizioni d'uso dell'app"
    val termsUrl: String get() = "https://emabuiadev.github.io/pokevault/terms"
    val tikTokLabel: String get() = "Seguici su TikTok"
    val tikTokSubtitle: String get() = "Se vuoi essere aggiornato nel mondo Pokemon, seguici su @pokevault94"
    val tikTokUrl: String get() = "https://www.tiktok.com/@pokevault94"
    val logoutLabel: String get() = "Esci"
    val logoutSubtitle: String get() = "Disconnettiti dal tuo account"
    val dangerZone: String get() = "Zona Pericolosa"
    val deleteAccountButton: String get() = "Elimina Account"
    val deleteAccountTitle: String get() = "Eliminare l'account?"
    val deleteAccountMessage: String get() =
        "Questa azione è irreversibile. Tutti i tuoi dati verranno eliminati definitivamente:\n\n" +
            "• Profilo utente\n• Collezione di carte\n• Mazzi salvati\n• Carte graduate\n\n" +
            "Sei sicuro di voler procedere?"
    val deleteAccountConfirm: String get() = "Elimina definitivamente"
    val deletingAccount: String get() = "Eliminazione in corso..."
    val disclaimerTitle: String get() = "Disclaimer"
    val disclaimerBody: String get() =
        "Pokémon, Pokémon TCG e tutti i nomi, le immagini e i marchi correlati sono " +
            "proprietà di Nintendo, The Pokémon Company e The Pokémon Company International. " +
            "Questa app non è affiliata, sponsorizzata o approvata da Nintendo, " +
            "The Pokémon Company o The Pokémon Company International.\n\n" +
            "Prezzi di mercato e catalogo carte in altre lingue sono recuperati da Pokewallet.io (api.pokewallet.io) " +
            "tramite i nostri server, mai direttamente dal tuo dispositivo. Sono utilizzati esclusivamente a scopo " +
            "informativo e di gestione della collezione personale. Per segnalazioni relative al copyright: " +
            "emabuiadev.github.io/pokevault/copyright\n\n" +
            "Tutti gli altri marchi appartengono ai rispettivi proprietari."

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
