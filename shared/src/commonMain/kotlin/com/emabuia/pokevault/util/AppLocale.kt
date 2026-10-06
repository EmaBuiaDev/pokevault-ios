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
    val retry: String get() = "Riprova"
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
    val wishlistBudgetHint: String get() = "Il tetto che ti dai per questa lista"
    fun wishlistCardsCount(count: Int) = "$count carte"
    val wishlistPickerSubtitle: String get() = "Una carta può stare in più liste"
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

    // Collezione: gli stessi testi dell'app Android.
    val addCard: String get() = "Aggiungi carta"
    val categoryAll: String get() = "Tutte"
    val categoryEnergy: String get() = "Energia"
    val categoryPokemon: String get() = "Pokémon"
    val categoryTrainer: String get() = "Allenatore"
    val changeGridDensity: String get() = "Cambia densita griglia"
    val changeView: String get() = "Cambia vista"
    val collapseAll: String get() = "Chiudi tutte"
    val collectionLayoutAll: String get() = "Tutte"
    val collectionLayoutByExpansion: String get() = "Per espansione"
    val collectionStatCards: String get() = "Carte"
    val collectionStatUnique: String get() = "Uniche"
    val collectionStatValue: String get() = "Valore"
    val confirm: String get() = "Conferma"
    val delete: String get() = "Elimina"
    val deselectAll: String get() = "Deseleziona"
    val emptyCollectionTitle: String get() = "Nessuna carta trovata"
    val expandAll: String get() = "Espandi tutte"
    val expansionOrderMostValue: String get() = "Più valore"
    val fewerCards: String get() = "Meno carte"
    val filterCategory: String get() = "Categoria"
    val filterExpansion: String get() = "Espansione"
    val filterLanguage: String get() = "Lingua"
    val filterOnlyDuplicates: String get() = "Solo doppioni"
    val filterType: String get() = "Tipo"
    val filterValue: String get() = "Valore per copia"
    val filterVariant: String get() = "Stampa"
    val filters: String get() = "Filtri"
    val filtersClear: String get() = "Azzera"
    val moreCards: String get() = "Piu carte"
    val myCardsSingleLine: String get() = "Le mie carte"
    val noResultsAction: String get() = "Azzera filtri e ricerca"
    val rarity: String get() = "Rarità"
    val searchExpansionHint: String get() = "Cerca un'espansione…"
    val selectAll: String get() = "Seleziona tutte"
    val showFewer: String get() = "Mostra meno"
    val sortMenuTitle: String get() = "Ordina"
    val sortNameAsc: String get() = "Nome A-Z"
    val sortNumber: String get() = "Numero"
    val sortPriceHigh: String get() = "Prezzo più alto"
    val sortPriceLow: String get() = "Prezzo più basso"
    val sortRecent: String get() = "Recenti"
    val sortSectionCards: String get() = "Carte"
    val sortSectionExpansions: String get() = "Espansioni"
    val value10to50: String get() = "10 – 50 €"
    val value1to10: String get() = "1 – 10 €"
    val valueNoPrice: String get() = "Senza prezzo"
    val valueOver50: String get() = "Oltre 50 €"
    val valueUnder1: String get() = "Meno di 1 €"
    fun collectionResults(shown: Int, total: Int): String = "$shown di $total carte"
    val noResultsTitle: String get() = "Nessuna carta con questi filtri"
    val emptyCollectionHint: String get() = "Aggiungi la prima carta dal Pokédex."
    fun expansionCardsAndCopies(cards: Int, copies: Int): String = "$cards carte · $copies copie"
    fun filterOnlyDuplicatesHint(count: Int): String = "$count carte di cui hai più di una copia"
    fun showCardsButton(count: Int): String = when (count) {
        0 -> "Nessuna carta"
        1 -> "Mostra 1 carta"
        else -> "Mostra $count carte"
    }
    fun filterShowAllExpansions(count: Int): String = "Mostra tutte ($count)"
    fun selectedCount(count: Int): String = "$count selezionate"

    // Wishlist modificabili: gli stessi testi dell'app Android.
    val wishlistAddToList: String get() = "Aggiungi alla wishlist"
    val wishlistAlreadyIn: String get() = "Già dentro"
    val wishlistBudget: String get() = "Budget"
    val wishlistBudgetOptional: String get() = "Facoltativo"
    val wishlistChooseColor: String get() = "Colore"
    val wishlistChooseIcon: String get() = "A cosa serve questa lista"
    val wishlistCreate: String get() = "Crea Wishlist"
    val wishlistCreateNewList: String get() = "Nuova lista"
    val wishlistEmptySubtitle: String get() = "Crea la tua prima lista dei desideri"
    val wishlistName: String get() = "Nome lista"
    val wishlistNamePlaceholder: String get() = "Es. Chase cards Kanto"
    val wishlistEdit: String get() = "Modifica Wishlist"
    val wishlistEmpty: String get() = "Nessuna wishlist"
    val wishlistDeleteTitle: String get() = "Eliminare wishlist?"
    val wishlistDeleteMessage: String get() = "Questa azione è irreversibile."
    val wishlistCardsEmpty: String get() = "Nessuna carta in questa wishlist"
    val wishlistCardsEmptySubtitle: String get() = "Aggiungile dal dettaglio di una carta"
    val wishlistRemoveCardTitle: String get() = "Rimuovere carta dalla wishlist?"
    val wishlistFreeLimit: String get() = "Con l'account gratuito puoi tenere una wishlist. Con Premium ne crei quante vuoi."
    val save: String get() = "Salva"

    // Sezione illustratori: gli stessi testi dell'app Android.
    val illustrator: String get() = "Illustratore"
    val illustratorsTitle: String get() = "Illustratori"
    val illustratorsSubtitle: String get() = "Colleziona per artista"
    fun illustratorsCount(artists: Int): String = "$artists artisti"
    fun illustratorsSets(sets: Int): String = "$sets espansioni"
    val illustratorsFollowed: String get() = "Seguiti"
    val illustratorsAll: String get() = "Tutti"
    val illustratorFollow: String get() = "Segui"
    val illustratorUnfollow: String get() = "Smetti di seguire"
    val illustratorSearchHint: String get() = "Cerca un illustratore..."
    val illustratorSortClosest: String get() = "Quasi fatti"
    val illustratorSortCards: String get() = "Più carte"
    val illustratorSortName: String get() = "A-Z"
    val illustratorFilterMissing: String get() = "Mancanti"
    val illustratorFilterOwned: String get() = "Possedute"
    fun illustratorCardsAndSets(cards: Int, sets: Int): String = "$cards carte · $sets espansioni"
    val illustratorsEmptyTitle: String get() = "Nessun illustratore"
    val illustratorsEmptySubtitle: String get() = "Il catalogo non è raggiungibile in questo momento."
    val illustratorsNoMatch: String get() = "Nessun illustratore con questo nome"
    fun illustratorsCatalogGap(cards: Int): String =
        "$cards carte del catalogo non dicono chi le ha disegnate e non compaiono qui."
    fun illustratorsCollectionGap(cards: Int): String =
        "$cards carte della tua collezione non sono collegate al catalogo italiano e non contano nei progressi."
    val illustratorCompleteTitle: String get() = "Collezione completa!"
    fun illustratorCompleteSubtitle(name: String): String = "Hai tutte le carte disegnate da $name."
    val searchCards: String get() = "Cerca carte"
    val chaseStatOwned: String get() = "Possedute:"
    val chaseStatMissing: String get() = "Mancanti:"

    // Carte gradate: gli stessi testi dell'app Android.
    val gradedTitle: String get() = "Carte gradate"
    fun gradedSlabsCount(count: Int): String = if (count == 1) "1 slab" else "$count slab"
    val gradedStatSlabs: String get() = "Slab"
    val gradedStatAverage: String get() = "Voto medio"
    val gradedStatValue: String get() = "Valore"
    val gradedSearchHint: String get() = "Cerca fra le gradate"
    val gradedSpread: String get() = "Distribuzione voti"
    /** "Gem Mint 10" e' il nome che l'ente stampa: non si traduce. */
    fun gradedGemCount(count: Int): String = "$count Gem Mint 10"
    val gradedAllCompanies: String get() = "Tutti gli enti"
    val gradedNoCompany: String get() = "Senza ente"
    val gradedAllGrades: String get() = "Tutti i voti"
    val gradedTierGem: String get() = "Gem Mint 10"
    val gradedTierMint: String get() = "Mint 9 – 9.5"
    val gradedTierNearMint: String get() = "8 – 8.5"
    val gradedTierExcellent: String get() = "6 – 7.5"
    val gradedTierPlayed: String get() = "Sotto 6"
    val gradedTierUngraded: String get() = "Senza voto"
    val gradedSort: String get() = "Ordina"
    val gradedSortGrade: String get() = "Voto più alto"
    val gradedSortValue: String get() = "Valore"
    val gradedSortName: String get() = "Nome"
    val gradedSortRecent: String get() = "Aggiunte di recente"
    val gradedEmptyTitle: String get() = "Nessuna carta gradata"
    val gradedEmptySubtitle: String get() =
        "Apri una carta della collezione e segnala come gradata: ente, voto e valore finiscono qui."
    val gradedNoResults: String get() = "Nessuna slab con questi filtri"
    fun gradedUnpricedNote(count: Int): String =
        if (count == 1) "1 slab senza valore stimato: il totale è al ribasso."
        else "$count slab senza valore stimato: il totale è al ribasso."
    fun gradedUngradedNote(count: Int): String = if (count == 1) "1 slab senza voto" else "$count slab senza voto"
    val gradedLoadError: String get() = "Impossibile caricare le carte gradate"
    val resetFilters: String get() = "Reset"
    val noSet: String get() = "Senza set"
    val gradedCardSection: String get() = "Carta Gradata"
    val insertInGradedCards: String get() = "Inserisci nelle carte gradate"
    val gradeLabel: String get() = "Voto (1-10)"
    val gradingAgency: String get() = "Ente"
    val gradingGradeRequired: String get() = "Inserisci il voto per una carta gradata"
    val gradingCompanyRequired: String get() = "Seleziona la societa' di grading"

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

    // Collector Lab: album e chase: gli stessi testi dell'app Android.
    val albumAddCards: String get() = "Aggiungi carte"
    fun albumAddSelected(count: Int): String = "Aggiungi $count carte"
    val albumAllCategories: String get() = "Tutte le categorie"
    val albumAllExpansions: String get() = "Tutte le espansioni"
    val albumAllTypes: String get() = "Tutti i tipi"
    fun albumBinderPage(current: Int, total: Int): String = "Pagina $current di $total"
    val albumCardRemoved: String get() = "Carta rimossa dall'album"
    fun albumCardsAdded(count: Int): String = run {
        if (count == 1) "1 carta aggiunta all'album" else "$count carte aggiunte all'album"
    }
    val albumCategory: String get() = "Categoria"
    val albumCoverUpdated: String get() = "Copertina aggiornata"
    val albumCreateCta: String get() = "Crea un album"
    val albumCustomSizeLabel: String get() = "Numero carte personalizzato"
    val albumDeleteMessage: String get() = "Questa azione è irreversibile. Le carte nella collezione non verranno eliminate."
    val albumDeleteTitle: String get() = "Eliminare album?"
    val albumDescription: String get() = "Descrizione (opzionale)"
    val albumEmpty: String get() = "Nessun album creato"
    val albumEmptySubtitle: String get() = "Crea il tuo primo album personalizzato!"
    val albumExpansion: String get() = "Espansione"
    val albumExpansionsLabel: String get() = "Espansioni"
    val albumFilledLabel: String get() = "Riempimento"
    val albumFull: String get() = "Album pieno"
    val albumMoveBack: String get() = "Sposta indietro"
    val albumMoveFirst: String get() = "Porta all'inizio"
    val albumMoveForward: String get() = "Sposta avanti"
    val albumName: String get() = "Nome Album"
    val albumNamePlaceholder: String get() = "Es. I miei preferiti di fuoco"
    val albumNoMatchingCards: String get() = "Nessuna carta compatibile trovata nella collezione"
    val albumNoResults: String get() = "Nessun album corrisponde alla ricerca"
    val albumNotFound: String get() = "Album non trovato"
    val albumOpenCard: String get() = "Apri dettaglio"
    val albumPokemonType: String get() = "Tipo Pokémon"
    val albumRemoveCard: String get() = "Rimuovi dall'album"
    val albumSearchHint: String get() = "Cerca un album"
    val albumSearchPlaceholder: String get() = "Nome, set o numero carta"
    val albumSetCover: String get() = "Imposta come copertina"
    val albumSize: String get() = "Grandezza Album"
    val albumSizeCustom: String get() = "Personalizzato"
    fun albumSlots(used: Int, total: Int) = "$used / $total carte"
    fun albumSlotsLeft(count: Int): String = run {
        if (count == 1) "1 slot libero" else "$count slot liberi"
    }
    val albumSortFill: String get() = "Riempimento"
    val albumSortName: String get() = "Nome"
    val albumSortRecent: String get() = "Recenti"
    val albumSortValue: String get() = "Valore"
    val albumTheme: String get() = "Tematica"
    val albumThemeClassic: String get() = "Classico"
    val albumThemeDark: String get() = "Oscuro"
    val albumThemeElectric: String get() = "Elettro"
    val albumThemeFire: String get() = "Fuoco"
    val albumThemeGrass: String get() = "Erba"
    val albumThemePsychic: String get() = "Psico"
    val albumThemeWater: String get() = "Acqua"
    val albumTitle: String get() = "Collector Lab"
    val albumValueLabel: String get() = "Valore album"
    val albumViewBinder: String get() = "Raccoglitore"
    val albumViewGrid: String get() = "Griglia"
    fun albumsSummary(albums: Int, cards: Int): String = "$albums album · $cards carte"
    val chaseAddMissingToWishlist: String get() = "Mancanti nella wishlist"
    val chaseBiggestHurdle: String get() = "La mancante più cara"
    val chaseCardAddError: String get() = "Errore durante l'aggiunta"
    val chaseCardAdded: String get() = "Carta aggiunta alla collezione"
    val chaseCardMissingLabel: String get() = "Mancante"
    val chaseCardsNoResults: String get() = "Nessuna carta trovata"
    fun chaseCardsProgress(owned: Int, total: Int): String = "$owned / $total carte"
    val chaseCompleteBadge: String get() = "Completo"
    val chaseCompletedSubtitle: String get() = "Hai tutte le carte di questo obiettivo."
    val chaseCompletedTitle: String get() = "Chase completato"
    val chaseCompletionCost: String get() = "Costo per completare"
    fun chaseCompletionCostNote(priced: Int, total: Int): String = "stima su $priced mancanti di $total con prezzo noto"
    val chaseCreateCta: String get() = "Crea un chase"
    val chaseCriteriaValueLabel: String get() = "Seleziona valore"
    val chaseCustomSearchLabel: String get() = "Cerca carte da aggiungere"
    val chaseDeleteMessage: String get() = "Questa azione è irreversibile. Le carte nella collezione non verranno eliminate."
    val chaseDeleteTitle: String get() = "Eliminare Chase?"
    val chaseDensityToggle: String get() = "Cambia densità"
    val chaseEmptyAll: String get() = "Nessuna carta disponibile"
    val chaseEmptyDuplicates: String get() = "Nessun duplicato"
    val chaseEmptyMissing: String get() = "🎉 Hai completato questo chase!"
    val chaseEmptyOwned: String get() = "Non possiedi ancora nessuna carta di questo chase"
    val chaseLoadError: String get() = "Impossibile caricare le carte di questo chase"
    fun chaseMissingCount(count: Int): String = run {
        if (count == 1) "1 mancante" else "$count mancanti"
    }
    val chaseNameLabel: String get() = "Nome Chase"
    val chaseNextCheapest: String get() = "La mancante più economica"
    val chaseNoResults: String get() = "Nessun chase corrisponde alla ricerca"
    val chaseNotFound: String get() = "Chase non trovato"
    val chaseOwnedBadge: String get() = "In collezione"
    val chaseSearchCards: String get() = "Cerca fra le carte"
    val chaseSearchHint: String get() = "Cerca un chase"
    val chaseSortCardName: String get() = "Nome"
    val chaseSortCardsLabel: String get() = "Ordina carte"
    val chaseSortClosest: String get() = "Quasi fatti"
    val chaseSortName: String get() = "Nome"
    val chaseSortNumber: String get() = "Numero"
    val chaseSortPriceAsc: String get() = "Prezzo ↑"
    val chaseSortPriceDesc: String get() = "Prezzo ↓"
    val chaseSortRecent: String get() = "Recenti"
    val chaseStatDuplicates: String get() = "Doppie:"
    val chaseStatTotal: String get() = "Totale:"
    fun chaseTabAll(count: Int): String = "Tutte ($count)"
    fun chaseTabDuplicates(count: Int): String = "Doppie ($count)"
    fun chaseTabMissing(count: Int): String = "Mancanti ($count)"
    fun chaseTabOwned(count: Int): String = "Possedute ($count)"
    fun chaseWishlistAdded(count: Int): String = run {
        if (count == 1) "1 carta aggiunta alla wishlist" else "$count carte aggiunte alla wishlist"
    }
    val chaseWishlistError: String get() = "Impossibile aggiornare la wishlist"
    val chaseWishlistNoList: String get() = "Crea prima una wishlist dal menu Wishlist"
    val collectorAlbumEmptyHint: String get() = "Metti in pagina le carte che hai, come in un raccoglitore vero."
    val collectorAlbumHint: String get() = "Le tue carte, pagina per pagina"
    val collectorAlbumTitle: String get() = "Album"
    val collectorChaseEmptyHint: String get() = "Scegli un set e guarda quali carte ti mancano per chiuderlo."
    val collectorChaseHint: String get() = "Un set da chiudere, carta per carta"
    val collectorChaseTitle: String get() = "Chase"
    fun collectorChasesDone(done: Int, total: Int): String = "$done di $total completati"
    fun collectorDiscoverIllustrators(count: Int): String = "Tutti i $count artisti"
    val collectorIllustratorsEmptyHint: String get() = "Scegli un artista e colleziona tutte le carte che ha disegnato."
    val collectorIllustratorsHint: String get() = "Tutte le carte di un artista"
    val collectorIntroTitle: String get() = "Tre modi di collezionare"
    val collectorLabSubtitle: String get() = "Album, chase e illustratori"
    fun collectorMissingCards(count: Int): String = run {
        if (count == 1) "1 carta mancante" else "$count carte mancanti"
    }
    fun collectorMissingToFinish(count: Int): String = run {
        if (count == 1) "Manca 1 carta per chiudere" else "Mancano $count carte per chiudere"
    }
    val collectorNewAlbum: String get() = "Nuovo album"
    val collectorNewChase: String get() = "Nuovo chase"
    val collectorNextStep: String get() = "Il prossimo passo"
    val collectorSeeAll: String get() = "Vedi tutti"
    val createAlbum: String get() = "Crea Album"
    val createChaseTitle: String get() = "Nuovo Chase"
    val criteriaCustom: String get() = "Personalizzato"
    val criteriaRarity: String get() = "Rarità"
    val criteriaSet: String get() = "Set"
    val criteriaSupertype: String get() = "Categoria"
    val criteriaType: String get() = "Tipo"
    val editAlbum: String get() = "Modifica Album"
    val myAlbums: String get() = "I miei Album"
    val newChaseLabel: String get() = "Nuovo Chase"
    val newChaseSubtitle: String get() = "Crea un album obiettivo per completare un set o una rarità"
    fun otherCardsCount(count: Int): String = "+ $count altre carte"
    // Su iOS non si compra ancora niente: il limite si dice, senza mandare a comprare altrove.
    val premiumAlbumLimitMessage: String get() = "Con l'account gratuito puoi tenere un album. Con Premium ne crei quanti vuoi."
    val premiumAlbumLimitTitle: String get() = "Limite Album raggiunto"
    val premiumChaseLimitMessage: String get() = "Con l'account gratuito puoi tenere un Chase. Con Premium ne crei quanti vuoi."
    val premiumChaseLimitTitle: String get() = "Limite Chase raggiunto"
    val priceUnavailable: String get() = "Prezzo N/D"
    val saveChase: String get() = "Salva Chase"
    val searchCardPlaceholder: String get() = "Cerca carte"
    val searchSet: String get() = "Cerca un set..."
    val set: String get() = "Set"
    val undo: String get() = "Annulla"

    /** I tipi da scegliere per un album: typesIt dell'app Android. */
    fun getTypes(): List<String> = listOf(
        "Fuoco", "Acqua", "Erba", "Elettro", "Psico", "Lotta",
        "Buio", "Metallo", "Drago", "Folletto", "Normale", "Incolore",
    )

    // Competitive e Match log: gli stessi testi dell'app Android.
    val addMatch: String get() = "Registra Partita"
    val addTournament: String get() = "Registra Torneo"
    val competitiveDeckLabTab: String get() = "Deck Lab"
    val competitiveHandSimulatorTab: String get() = "Hand-Simulator"
    fun competitiveHubDeckCount(count: Int): String = if (count == 1) "1 deck" else "$count deck"
    val competitiveHubNeedsDeck: String get() = "Serve un deck per simulare"
    val competitiveHubNoDecks: String get() = "Nessun deck ancora"
    val competitiveHubNoMatches: String get() = "Nessuna partita registrata"
    val competitiveHubReadyToDraw: String get() = "Pronto: pesca la prima mano"
    fun competitiveHubRecord(wins: Int, losses: Int, ties: Int): String = "$wins-$losses-$ties"
    fun competitiveHubWinRate(rate: Int): String = "$rate% di vittorie"
    val competitiveLogTab: String get() = "Match Log"
    val competitiveTitle: String get() = "Competitive"
    val deckLabSubtitle: String get() = "Crea e gestisci i tuoi mazzi"
    val editMatch: String get() = "Modifica Partita"
    val editTournament: String get() = "Modifica Torneo"
    val matchDeleteMessage: String get() = "Questa azione è irreversibile."
    val matchDeleteTitle: String get() = "Eliminare partita?"
    val matchLogEmpty: String get() = "Nessuna partita registrata"
    val matchLogEmptySubtitle: String get() = "Registra la tua prima partita!"
    val matchLogTabStats: String get() = "Statistiche"
    val matchLogTabTournaments: String get() = "Tornei"
    val matchLogTitle: String get() = "Match Log"
    val matchLoss: String get() = "Sconfitta"
    val matchNotes: String get() = "Note"
    val matchNotesPlaceholder: String get() = "Tech particolari, matchup, sensazioni..."
    val matchOpponent: String get() = "Avversario"
    val matchOpponentDeck: String get() = "Mazzo avversario"
    val matchOpponentDeckSuggestions: String get() = "Gia' incontrati"
    val matchOpponentName: String get() = "Nome avversario"
    fun matchRecord(wins: Int, losses: Int, ties: Int) = "$wins W - $losses L - $ties T"
    val matchResult: String get() = "Risultato"
    val matchRound: String get() = "Turno"
    val matchRoundPlaceholder: String get() = "Es. 1"
    val matchStatsBestMatchup: String get() = "Il tuo migliore"
    fun matchStatsDeckTournaments(count: Int): String = if (count == 1) "1 torneo" else "$count tornei"
    val matchStatsDecksHint: String get() = "Il bilancio di ogni mazzo che hai portato a un torneo."
    val matchStatsDecksTitle: String get() = "I tuoi mazzi"
    val matchStatsEmptyBody: String get() = "Registra le partite di un torneo: da li' nascono i matchup, il bilancio dei tuoi mazzi e l'andamento."
    val matchStatsEmptyTitle: String get() = "Ancora nessuna partita"
    fun matchStatsFewGames(min: Int): String = "Serve almeno $min partite per un giudizio"
    val matchStatsFormHint: String get() = "Dalla piu' recente"
    val matchStatsFormTitle: String get() = "Ultime partite"
    val matchStatsMatchupsHint: String get() = "Come vai contro ogni archetipo che hai incontrato."
    val matchStatsMatchupsTitle: String get() = "Matchup"
    fun matchStatsMissingOpponentDeck(count: Int): String = if (count == 1) "1 partita senza mazzo avversario" else "$count partite senza mazzo avversario"
    val matchStatsNoMatchupsBody: String get() = "Il matchup si costruisce dal campo \"mazzo avversario\": compilalo quando registri una partita e questa tabella si riempie da sola."
    val matchStatsNoMatchupsTitle: String get() = "Nessun mazzo avversario registrato"
    val matchStatsNoWinRate: String get() = "solo pari"
    fun matchStatsPlayed(count: Int): String = if (count == 1) "1 partita giocata" else "$count partite giocate"
    val matchStatsWinRateLabel: String get() = "Vittorie"
    val matchStatsWorstMatchup: String get() = "Il tuo peggiore"
    val matchTie: String get() = "Pareggio"
    val matchWin: String get() = "Vittoria"
    val matchWinRate: String get() = "Win Rate"
    fun playersCount(count: Int): String = "$count giocatori"
    val premiumTournamentLimitMessage: String get() = "Hai raggiunto il limite di 1 torneo gratuito.\n\nPassa a Premium per registrare tornei illimitati!"
    val premiumTournamentLimitTitle: String get() = "Limite Torneo raggiunto"
    val tournamentDate: String get() = "Data"
    val tournamentDeck: String get() = "Deck"
    val tournamentDeckCustom: String get() = "Scrivi il nome"
    val tournamentDeckFromList: String get() = "Scegli dai tuoi deck"
    val tournamentDeckPlaceholder: String get() = "Es. Charizard ex"
    val tournamentDeleteMessage: String get() = "Verranno eliminate anche tutte le partite. Questa azione è irreversibile."
    val tournamentDeleteTitle: String get() = "Eliminare torneo?"
    val tournamentEmpty: String get() = "Nessun torneo registrato"
    val tournamentEmptySubtitle: String get() = "Registra il tuo primo torneo!"
    val tournamentFee: String get() = "Budget iscrizione"
    val tournamentFeePlaceholder: String get() = "Es. 15.00"
    val tournamentFormat: String get() = "Formato"
    val tournamentListTitle: String get() = "Tornei"
    val tournamentLocation: String get() = "Luogo"
    val tournamentLocationPlaceholder: String get() = "Es. Game Store Milano"
    fun tournamentMatches(count: Int) = "$count partite"
    val tournamentParticipants: String get() = "Partecipanti"
    val tournamentParticipantsPlaceholder: String get() = "Es. 32"
    val tournamentType: String get() = "Tipologia"
    val handSimulatorScoreLabel: String get() = "Consistenza"

    // Hand simulator: gli stessi testi dell'app Android.
    val handSimulatorBelowTarget: String get() = "sotto soglia"
    fun handSimulatorTarget(target: Int): String = "obiettivo $target%"
    val handVerdictGreat: String get() = "Mano ottima"
    val handVerdictGreatWhy: String get() = "Parte, mette energia e ha con cosa continuare."
    val handVerdictMulligan: String get() = "Mulligan obbligato"
    val handVerdictMulliganWhy: String get() = "Nessun Pokémon Basic: per regolamento si rimescola."
    val handVerdictPlayable: String get() = "Giocabile"
    val handVerdictPlayableWhy: String get() = "Parte, ma dipende da cosa peschi."
    val handVerdictRisky: String get() = "Rischiosa"
    val handVerdictRiskyWhy: String get() = "Legale ma povera: senza una pescata buona resti fermo."

    // Hand simulator, schermata: gli stessi testi dell'app Android.
    fun handCountBasics(count: Int): String = "$count Basic"
    fun handCountEnergy(count: Int): String = if (count == 1) "1 energia" else "$count energie"
    fun handCountOuts(count: Int): String = if (count == 1) "1 out" else "$count out"
    fun handCountSupporters(count: Int): String = if (count == 1) "1 supporter" else "$count supporter"
    val handHasKeyCard: String get() = "key card in mano"
    val handMissingEnergy: String get() = "niente energia"
    val handMissingKeyCard: String get() = "niente key card"
    val handMissingOut: String get() = "niente pescata"
    val handSimulatorAccuracyTitle: String get() = "Attenzione all'accuratezza"
    val handSimulatorAllMetrics: String get() = "Tutte le metriche"
    val handSimulatorAvgBasics: String get() = "Basic medi in mano"
    val handSimulatorAvgMulligans: String get() = "Mulligan medi"
    val handSimulatorClearKeyCards: String get() = "Pulisci"
    val handSimulatorDealButton: String get() = "Pesca una mano"
    val handSimulatorDecideHint: String get() = "Decidi come al tavolo: la tieni o la rimescoli?"
    val handSimulatorDeckEmpty: String get() = "Deck esaurito"
    fun handSimulatorDeckSizeWarning(actual: Int, expected: Int): String = "Il mazzo ha $actual carte invece di $expected: tutte le probabilita' sono calcolate su $actual."
    val handSimulatorDone: String get() = "Fatto"
    fun handSimulatorDrawTurn(turn: Int): String = "Pesca T$turn"
    val handSimulatorEnergyT1: String get() = "Energia entro T1"
    fun handSimulatorFreeLimitInfo(usedRuns: Int): String = "Free: 1 run per deck (usati: $usedRuns/1), valido solo se hai 1 deck totale."
    val handSimulatorHowItWorksBody: String get() = "Prova: peschi una mano vera dal deck e decidi se tenerla o rimescolare, come al tavolo. Puoi pescare il turno successivo per vedere se la mano si sblocca.\n\nAnalisi: il simulatore ripete la pescata migliaia di volte e riassume tutto in un punteggio di consistenza, con le metriche che lo compongono."
    val handSimulatorHowItWorksTitle: String get() = "Come funziona"
    fun handSimulatorInsightBrickMessage(value: Int): String = "Mulligan rate al $value%. Valuta piu starter Basic o outs di ricerca iniziale."
    fun handSimulatorInsightEnergyMessage(value: Int): String = "Energia entro T1 al $value%. Aumenta energia o recovery/search dedicata."
    fun handSimulatorInsightOutMessage(value: Int): String = "Out entro T1 al $value%. Inserisci piu carte draw/search per aumentare consistenza."
    fun handSimulatorInsightSetupMessage(value: Int): String = "Setup entro T2 al $value%. Ottimizza linea starter, energia e supporter."
    val handSimulatorInvalidDeck: String get() = "Deck non valido: servono almeno 7 carte"
    val handSimulatorKeepButton: String get() = "Tengo"
    fun handSimulatorKeepRate(rate: Int): String = "tieni il $rate% delle mani"
    val handSimulatorKeyByT2: String get() = "Key cards entro T2"
    val handSimulatorKeyCardsHint: String get() = "Le carte che decidono il turno. Il simulatore misura ogni quanto ne vedi almeno una entro il T2."
    val handSimulatorMetricAvgBasics: String get() = "Basic medi in mano: media dei Basic nelle prime 7 carte di ogni simulazione."
    val handSimulatorMetricAvgMulligans: String get() = "Mulligan medi: numero medio di mulligan necessari prima di una mano legale."
    val handSimulatorMetricEnergyShort: String get() = "Energia T1"
    val handSimulatorMetricEnergyT1: String get() = "Energia entro T1: % di run con almeno 1 energia in mano entro il turno 1."
    val handSimulatorMetricKeyByT2: String get() = "Key cards entro T2: % di run in cui trovi almeno una tra le Key Card selezionate entro T2."
    val handSimulatorMetricKeyShort: String get() = "Key card T2"
    val handSimulatorMetricMulligan: String get() = "Opening senza Starter: % di prime mani (7 carte) senza alcun Pokémon Base. Il giocatore dovrà scartare e pescare nuove carte fino ad averne una con Starter."
    val handSimulatorMetricOutShort: String get() = "Out T1"
    val handSimulatorMetricOutT1: String get() = "Out entro T1: % di run con almeno una carta di uscita (draw/search) entro T1."
    val handSimulatorMetricRuns: String get() = "Run: numero di mani simulate."
    val handSimulatorMetricScore: String get() = "Consistenza: media pesata delle metriche qui sotto, da 0 a 100."
    val handSimulatorMetricSetupShort: String get() = "Setup T2"
    val handSimulatorMetricSetupT2: String get() = "Setup entro T2: % di run con stato di setup minimo entro T2 (Basic + energia + out/supporter)."
    val handSimulatorMetricStarter: String get() = "Starter rate: % di opening hand con almeno 1 Basic."
    val handSimulatorMetricStarterShort: String get() = "Parte"
    val handSimulatorModeAnalysis: String get() = "Analisi"
    val handSimulatorModePractice: String get() = "Prova"
    val handSimulatorMulliganButton: String get() = "Mulligan"
    val handSimulatorMulliganRate: String get() = "Opening senza Starter"
    fun handSimulatorMulligansTaken(count: Int): String = if (count == 1) "1 mulligan preso" else "$count mulligan presi"
    val handSimulatorNoDecks: String get() = "Nessun deck disponibile"
    val handSimulatorNoDecksSubtitle: String get() = "Crea o importa un deck nel Deck Lab"
    val handSimulatorNoSearchResults: String get() = "Nessuna carta con questo nome nel deck"
    val handSimulatorNotRunBody: String get() = "Migliaia di mani in un secondo, per vedere ogni quanto il deck parte davvero."
    val handSimulatorNotRunTitle: String get() = "Ancora nessuna analisi"
    val handSimulatorOpeningLabel: String get() = "Apertura"
    val handSimulatorOutT1: String get() = "Out entro T1"
    val handSimulatorPracticeEmptyBody: String get() = "Peschi mani vere dal deck, una alla volta, come al tavolo. Nessuna impostazione da compilare."
    val handSimulatorPracticeEmptyTitle: String get() = "Scegli un deck e pesca"
    val handSimulatorPremiumUnlimited: String get() = "Premium attivo: simulazioni illimitate."
    val handSimulatorProblemsTitle: String get() = "Mani problematiche rilevate"
    val handSimulatorRunAgain: String get() = "Rilancia"
    val handSimulatorRunButton: String get() = "Avvia simulazione"
    val handSimulatorRunning: String get() = "Simulazione in corso..."
    val handSimulatorRunsLabel: String get() = "Mani da simulare"
    fun handSimulatorRunsRecap(runs: Int): String = "su $runs mani simulate"
    val handSimulatorSaveProblem: String get() = "Salva mano"
    val handSimulatorSavedEmpty: String get() = "Nessuna mano salvata per questo deck."
    val handSimulatorSavedTitle: String get() = "Vault locale"
    val handSimulatorSavedToast: String get() = "Mano salvata nel vault locale"
    val handSimulatorScoreExplain: String get() = "Media pesata di starter, setup T2, out T1 ed energia T1. Le key card entrano nel conto solo se le hai scelte."
    fun handSimulatorScoreVerdict(score: Int): String = when {
        score >= 80 -> if (isItalian) "Il deck gira" else "The deck runs"
        score >= 65 -> if (isItalian) "Nella norma" else "Average"
        score >= 50 -> if (isItalian) "Da sistemare" else "Needs work"
        else -> if (isItalian) "Fragile" else "Fragile"
    }
    val handSimulatorSearchCard: String get() = "Cerca nel deck"
    val handSimulatorSelectDeck: String get() = "Seleziona un deck"
    val handSimulatorSelectKeyCards: String get() = "Seleziona Key Cards"
    val handSimulatorSettingsTitle: String get() = "Impostazioni"
    val handSimulatorSetupT2: String get() = "Setup entro T2"
    val handSimulatorStarterRate: String get() = "Starter rate"
    val handSimulatorTagMissKeyT2: String get() = "Miss Key T2"
    val handSimulatorTagNoBasicDeck: String get() = "Deck senza Basic"
    val handSimulatorTagNoEnergyT1: String get() = "No Energia T1"
    val handSimulatorTagNoOutT1: String get() = "No Out T1"
    val handSimulatorTagSetupRiskT2: String get() = "Setup Risk T2"
    fun handSimulatorTally(kept: Int, mulliganed: Int): String = "Tenute $kept · Mulligan $mulliganed"
    val handSimulatorTapCardHint: String get() = "Tocca una carta per ingrandirla"
    val handSimulatorTitle: String get() = "Hand-Simulator"
    val handSimulatorTotalRuns: String get() = "Run"
    fun handSimulatorTurnLabel(turn: Int): String = "Turno $turn"
    fun handSimulatorUnknownStageWarning(count: Int): String = "$count carte non hanno lo stadio nei dati e sono contate come Base: il tasso di mulligan puo' risultare piu' basso del reale."
    val premiumHandSimulatorMessage: String get() = "Con il piano gratuito puoi usare Hand-Simulator una sola volta per deck e solo se possiedi 1 deck totale.\n\nPassa a Premium per simulazioni illimitate."
    val premiumHandSimulatorTitle: String get() = "Hand-Simulator Premium"

    // Deck Lab: gli stessi testi dell'app Android.
    val add: String get() = "Aggiungi"
    fun deckDeleteBody(name: String): String = "\"$name\" verra' eliminato. L'operazione non si puo' annullare."
    val deckDeleteBodyTestDeck: String get() = "Le carte che tieni solo in questo deck di prova, e che nessun altro deck usa, verranno eliminate con lui."
    val deckDeleteTitle: String get() = "Eliminare il deck?"
    val deckExportCopied: String get() = "Decklist copiata"
    val deckExportCopy: String get() = "Copia"
    val deckExportShare: String get() = "Condividi"
    val deckExportShareChooser: String get() = "Condividi decklist"
    val deckExportTitle: String get() = "Export Decklist"
    val deckFilterAll: String get() = "Tutti"
    val deckFilterCollection: String get() = "Collezione"
    val deckFilterEmpty: String get() = "Nessun deck in questa categoria."
    val deckFilterTest: String get() = "Di prova"
    val deckLabMyDecksSubtitle: String get() = "Crea mini-deck con le carte che possiedi"
    val deckRemoveOneCopy: String get() = "Togli una copia"
    val deckTestBadge: String get() = "Deck di prova"
    val premiumDeckExportMessage: String get() = "L'export in formato decklist PTCG standard e disponibile solo con Premium.\n\nPassa a Premium per esportare e condividere i tuoi deck."
    val premiumDeckExportTitle: String get() = "Export Decklist Premium"
    val premiumDeckLimitMessage: String get() = "Hai raggiunto il limite di 1 deck gratuito.\n\nPassa a Premium per creare deck illimitati!"
    val premiumDeckLimitTitle: String get() = "Limite Deck raggiunto"

    // Deck Lab, editor: gli stessi testi dell'app Android.
    fun addCopiesToDeck(count: Int): String = "Aggiungi $count al deck"
    val addedToCollectionAndDeck: String get() = "Verranno aggiunte alla tua collezione e al deck"
    val clearSearch: String get() = "Cancella ricerca"
    val createNewDeck: String get() = "Crea un nuovo deck"
    fun deckCardRemoved(name: String): String = "$name tolta dal deck"
    val deckCardsInDeck: String get() = "Nel deck"
    val deckCloseEditor: String get() = "Chiudi editor"
    fun deckCover(index: Int): String = "Copertina deck $index"
    fun deckCoverChosen(count: Int): String = if (count == 1) "1 carta su 2" else "$count carte su 2"
    val deckCoverEmpty: String get() = "Nessuna copertina scelta"
    val deckCoverHint: String get() = "Fino a 2 Pokémon del deck. Sono quelli che rappresentano il mazzo nell’elenco e in cima al dettaglio. Se non scegli niente, ci pensa l’app."
    val deckCoverPreviewTitle: String get() = "Anteprima"
    val deckCoverTitle: String get() = "Copertina"
    val deckDiscardBody: String get() = "Le carte scelte e il nome andranno persi. Il deck non e' ancora stato salvato."
    val deckDiscardConfirm: String get() = "Scarta"
    val deckDiscardKeepEditing: String get() = "Continua"
    val deckDiscardTitle: String get() = "Scartare il deck?"
    val deckEditTitle: String get() = "Modifica deck"
    val deckGoToCards: String get() = "Rivedi le carte"
    val deckImportReviewBody: String get() = "Vedi solo le carte appena importate"
    val deckImportReviewTitle: String get() = "Revisione import"
    fun deckImportedBody(count: Int): String = "$count carte riconosciute. Controlla il nome, scegli la copertina e salva."
    val deckImportedTitle: String get() = "Deck importato"
    val deckInYourCollection: String get() = "Nella tua collezione"
    val deckNameLabel: String get() = "Nome del deck"
    val deckNamePlaceholder: String get() = "Nome deck..."
    val deckNewTitle: String get() = "Nuovo deck"
    val deckNoCardsInCategory: String get() = "Nessuna carta in questa categoria."
    val deckNoCardsYet: String get() = "Aggiungi prima qualche Pokémon: la copertina si sceglie fra quelli del deck."
    val deckNoLocalResults: String get() = "Nessuna carta con questo nome nella tua collezione."
    val deckOnlineResults: String get() = "Risultati online"
    fun deckPendingSelection(count: Int): String = "Aggiungi $count"
    val deckPlaceholderWarningBody: String get() = "Non sono nel catalogo italiano, di solito perche' l'espansione e' appena uscita. Sono state aggiunte lo stesso con i dati della decklist."
    fun deckPlaceholderWarningTitle(count: Int): String = if (count == 1) "1 carta senza immagine" else "$count carte senza immagine"
    val deckSaveChanges: String get() = "Salva modifiche"
    val deckSaveNew: String get() = "Salva deck"
    val deckSearchCards: String get() = "Cerca carte"
    val deckSearchOnline: String get() = "Cerca nei set online"
    val deckShowWholeCollection: String get() = "Tutta la collezione"
    val deckSourceCollection: String get() = "Aggiungile alla collezione"
    val deckSourceCollectionDesc: String get() = "Le carte entrano fra le tue e contano nel valore della collezione."
    val deckSourceDeckOnly: String get() = "Solo in questo deck"
    val deckSourceDeckOnlyDesc: String get() = "Deck di prova: il mazzo e' completo, ma la collezione non cambia."
    val deckSourceNewDeckQuestion: String get() = "Mentre costruisci il mazzo puoi aggiungere carte che non possiedi."
    val deckSourceSkip: String get() = "Lascia il deck incompleto"
    val deckSourceTitle: String get() = "Dove mettiamo le carte?"
    val deckStepCards: String get() = "Carte"
    val deckStepDetails: String get() = "Dettagli"
    val howManyCopiesToAdd: String get() = "Quante copie vuoi aggiungere?"
    val import: String get() = "Importa"
    val importAddingCards: String get() = "Aggiunta carte in corso..."
    val importAndMore: String get() = "e altre"
    val importCardsFound: String get() = "carte trovate su"
    val importDeck: String get() = "Importa Deck"
    val importLeftOutMessage: String get() = "Queste carte sono rimaste fuori dal deck. Puoi aggiungerle a mano dal passo Carte."
    val importMatchedMessage: String get() = "Le carte trovate sono state aggiunte al deck. Puoi modificarlo prima di salvare."
    val importMissingTitle: String get() = "Carte mancanti"
    val importNoMatchMessage: String get() = "Nessuna carta corrisponde alla tua collezione."
    val importResultTitle: String get() = "Risultato Import"
    val minus: String get() = "Meno"
    val ok: String get() = "OK"
    val plus: String get() = "Piu"
    fun selectCover(cardName: String): String = "Seleziona copertina $cardName"
    val selectedCover: String get() = "Copertina selezionata"

    // Deck Lab, import: gli stessi testi dell'app Android.
    fun deckSourceQuestion(missing: Int): String = "$missing carte del deck non sono nella tua collezione."

    // Meta Deck e Win Tournament: gli stessi testi dell'app Android.
    val connectionError: String get() = "Errore di connessione"
    fun deckCountLabel(count: Int): String = "$count mazzi"
    val deckLabMetaDeck: String get() = "Meta Deck"
    val deckLabMetaDeckSubtitle: String get() = "Archetipi e classifica del meta da LimitlessTCG"
    val deckLabMyDecks: String get() = "I Miei Deck"
    val deckLabWinTournament: String get() = "Win Tournament"
    val deckLabWinTournamentSubtitle: String get() = "Deck vincitori dai tornei competitivi"
    val importInDeckLab: String get() = "Importa in DeckLab"
    val metaArchetypeInfoBody: String get() = "Aggregato dagli ultimi 15 tornei competitivi su LimitlessTCG (top 32 di ogni torneo). Gli archetipi sono ordinati per meta share: la % di copie del deck nel pool competitivo."
    val metaInfoAction: String get() = "Da dove arrivano i dati"
    fun metaLastUpdatedHours(hours: Long): String = "Aggiornato ${hours}h fa"
    fun metaLastUpdatedMinutes(minutes: Long): String = "Aggiornato $minutes min fa"
    val metaLastUpdatedNow: String get() = "Aggiornato ora"
    val metaNoArchetypes: String get() = "Nessun archetipo trovato"
    val metaRateLimitedBody: String get() = "LimitlessTCG accetta 50 richieste ogni 5 minuti e per ora sono finite. Non c'e' niente da sistemare: basta aspettare."
    val metaRateLimitedStaleBody: String get() = "Stai vedendo gli ultimi dati salvati. Si aggiorneranno da soli."
    val metaRateLimitedTitle: String get() = "LimitlessTCG in pausa"
    fun metaRateLimitedWait(seconds: Long): String {
        val minutes = seconds / 60
        val rest = seconds % 60
        return when {
            minutes <= 0L -> if (isItalian) "Riprova fra ${seconds}s" else "Retry in ${seconds}s"
            rest == 0L -> if (isItalian) "Riprova fra ${minutes} min" else "Retry in ${minutes} min"
            else -> if (isItalian) "Riprova fra ${minutes} min ${rest}s" else "Retry in ${minutes} min ${rest}s"
        }
    }
    fun metaRefreshCooldown(seconds: Long): String = "Riprova tra ${seconds}s"
    val metaShare: String get() = "Meta Share"
    val noDecklistAvailable: String get() = "Nessuna decklist disponibile"
    val premiumMetaDeckLimitMessage: String get() = "Hai utilizzato tutte le 10 visualizzazioni gratuite dei Meta Deck.\n\nPassa a Premium per accesso illimitato!"
    val premiumMetaDeckLimitTitle: String get() = "Visualizzazioni Meta Deck esaurite"
    val refresh: String get() = "Aggiorna"
    val tryChangeFormat: String get() = "Prova a cambiare formato o riprova piu tardi."
    val unknownDeck: String get() = "Deck sconosciuto"
    val viewDeck: String get() = "Vedi deck"
    val winTournamentInfoBody: String get() = "I top 3 piazzati (con decklist) degli ultimi tornei su LimitlessTCG, dal piu' recente. Sono esclusi gli eventi sotto gli 8 giocatori, che non sono risultati competitivi. Tocca un piazzamento per vedere la decklist completa e importarla."
    val winTournamentKindAll: String get() = "Tutti"
    val winTournamentKindLive: String get() = "Dal vivo"
    val winTournamentKindOnline: String get() = "Online"
    val winTournamentLoading: String get() = "Caricamento tornei..."
    val winTournamentNoLiveResults: String get() = "Nessun torneo dal vivo di recente"
    val winTournamentNoLiveResultsBody: String get() = "Nelle ultime settimane su LimitlessTCG non risultano eventi in presenza per questo formato. Prova con Tutti o cambia formato."
    val winTournamentNoOnlineResults: String get() = "Nessun torneo online di recente"
    val winTournamentNoResults: String get() = "Nessun torneo trovato"
    fun winTournamentPlayers(count: Int): String = "$count giocatori"

    // Scanner: gli stessi testi dell'app Android.
    val discard: String get() = "Scarta"
    val goBack: String get() = "Torna indietro"
    val grantPermission: String get() = "Concedi permesso"
    val noneOfThese: String get() = "Nessuna di queste"
    val recognizedCard: String get() = "Carta riconosciuta"
    fun scannerAddUndone(name: String): String = "$name tolta dalla collezione."
    fun scannerAddedLabel(name: String): String = "Aggiunta: $name"
    val scannerAddedTitle: String get() = "Aggiunta!"
    val scannerAllRejected: String get() = "Le hai scartate tutte per questo numero."
    val scannerAlreadyAdded: String get() = "Già aggiunta: passa alla carta successiva."
    val scannerDismissedLabel: String get() = "Carta scartata"
    val scannerFillFrame: String get() = "Riempi la cornice con la carta"
    val scannerHintMissingId: String get() = "Non leggo il numero in basso a sinistra: avvicina la carta e riempi la cornice."
    val scannerManualSearch: String get() = "Cercala a mano"
    fun scannerMissingTotal(number: String): String = "Numero $number letto, ma non il totale del set. Avvicina la carta."
    val scannerModeConfirm: String get() = "A conferma"
    val scannerModeContinuous: String get() = "Continuo"
    fun scannerNotInCatalog(number: String, total: String): String = "Nessuna carta $number/$total nel catalogo italiano."
    val scannerPermissionNeeded: String get() = "Per usare lo scanner serve il permesso fotocamera."
    val scannerPermissionRationale: String get() = "La fotocamera serve per scansionare le carte Pokémon e aggiungerle alla collezione."
    val scannerPrint: String get() = "Stampa"
    val scannerRetryRejected: String get() = "Riproponi tutte"
    val scannerSameNumberHint: String get() = "Hanno lo stesso numero: cambia l'espansione."
    fun scannerSaveError(message: String): String = "Errore salvataggio: $message"
    fun scannerSearchError(message: String): String = "Errore ricerca: $message"
    val scannerSearching: String get() = "Cerco la carta…"
    fun scannerUndoFailed(message: String): String = "Non riesco ad annullare: $message"
    val scannerUnknownExpansion: String get() = "Espansione sconosciuta"
    val scannerWhichOne: String get() = "Quale di queste?"
}
