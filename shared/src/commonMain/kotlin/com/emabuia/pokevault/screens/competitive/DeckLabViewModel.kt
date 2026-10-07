package com.emabuia.pokevault.screens.competitive

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.Card
import com.emabuia.pokevault.data.CatalogRepository
import com.emabuia.pokevault.data.CollectionRepository
import com.emabuia.pokevault.data.CollectionWriter
import com.emabuia.pokevault.data.CompetitiveRepository
import com.emabuia.pokevault.data.ExpansionsState
import com.emabuia.pokevault.data.PremiumRepository
import com.emabuia.pokevault.data.WORKER_BASE_URL
import com.emabuia.pokevault.data.model.BasicEnergyResolver
import com.emabuia.pokevault.data.model.CardClassifier
import com.emabuia.pokevault.data.model.DeckImportParser
import com.emabuia.pokevault.data.model.MetaDeck
import com.emabuia.pokevault.data.model.MetaDeckCard
import com.emabuia.pokevault.data.remote.SetCodeMapper
import com.emabuia.pokevault.util.IllustratorNames
import com.emabuia.pokevault.data.model.Deck
import com.emabuia.pokevault.data.model.DeckAnalysis
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.data.remote.TcgImages
import com.emabuia.pokevault.data.remote.TcgSet
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Il Deck Lab: viewmodel/DeckLabViewModel.kt di Android, un pezzo alla volta.
 *
 * Ci sono l'elenco, il dettaglio, elimina, duplica, esporta e l'editor (crea
 * e modifica, con le carte della collezione e quelle cercate nel catalogo).
 * C'e' anche l'import, da testo e dai meta deck.
 *
 * Su Android i dati arrivano da snapshot listener; qui si rileggono a ogni
 * scrittura (repository.changes), con lo stesso effetto.
 */
class DeckLabViewModel(
    private val competitive: CompetitiveRepository,
    private val collection: CollectionRepository,
    private val premium: PremiumRepository,
    private val catalog: CatalogRepository,
    private val writer: CollectionWriter,
) : ViewModel() {

    var decks by mutableStateOf<List<Deck>>(emptyList())
        private set

    /**
     * Tutto quello che un deck puo' contenere: collezione + carte solo-deck.
     * Serve per *risolvere* gli id dentro ai deck, mai per dire cosa l'utente
     * possiede: per quello c'e' [ownedCards].
     */
    var allCards by mutableStateOf<List<PokemonCard>>(emptyList())
        private set

    /** Le sole carte possedute davvero. */
    val ownedCards: List<PokemonCard> by derivedStateOf {
        allCards.filter { !it.deckOnly }
    }

    private val allCardsById by derivedStateOf {
        allCards.associateBy { it.id }
    }

    var isLoading by mutableStateOf(true)
        private set

    var isSaving by mutableStateOf(false)
        private set

    /** null finche' il Worker non ha risposto: nel dubbio non si blocca niente. */
    var isPremium by mutableStateOf<Boolean?>(null)
        private set

    // ── Deck in modifica ────────────────────────────────────────────────────

    var editingDeckId by mutableStateOf<String?>(null)
    var newDeckName by mutableStateOf("")
    var selectedCardsIds by mutableStateOf<List<String>>(emptyList())
    var coverImageUrl by mutableStateOf("")
    var coverImageUrls by mutableStateOf<List<String>>(emptyList())
    var currentAnalysis by mutableStateOf(DeckAnalysis())
    var validationError by mutableStateOf<String?>(null)
        private set

    /** Il deck in modifica viene da un import: l'editor parte dai dettagli e mostra solo le carte del deck. */
    var isImportReviewMode by mutableStateOf(false)
        private set

    /**
     * Che fine fanno le carte che il deck usa e l'utente non possiede: una
     * proprieta' della sessione di modifica, come su Android.
     */
    enum class DeckCardSource {
        /** Le carte mancanti entrano in collezione: il deck e' fatto di carte tue. */
        COLLECTION,

        /** Le carte mancanti restano dentro al deck: deck di prova. */
        DECK_ONLY
    }

    var deckCardSource by mutableStateOf(DeckCardSource.COLLECTION)
        private set

    /** La scelta fatta prima di aprire l'editor di un deck nuovo. */
    fun chooseDeckCardSource(source: DeckCardSource) {
        deckCardSource = source
    }

    /**
     * Gli id delle carte solo-deck create in questa sessione: fra la scrittura
     * e la rilettura c'e' un istante in cui non sono ancora in [allCards].
     */
    private var sessionDeckOnlyCardIds by mutableStateOf<Set<String>>(emptySet())

    /** Il deck in modifica contiene almeno una carta non posseduta. */
    val editingDeckHasDeckOnlyCards: Boolean by derivedStateOf {
        selectedCardsIds.any { it in sessionDeckOnlyCardIds || allCardsById[it]?.deckOnly == true }
    }

    /** Le carte importate senza immagine: l'editor le elenca, invece di lasciarle comparire in silenzio. */
    var importPlaceholderNames by mutableStateOf<List<String>>(emptyList())
        private set

    // Ricerca nel catalogo ("Cerca nei set")
    var isSearchingCards by mutableStateOf(false)
        private set
    var tcgSearchResults by mutableStateOf<List<TcgCard>>(emptyList())
        private set
    var tcgSearchError by mutableStateOf<String?>(null)
        private set

    // Su allCards: mappa gli id che stanno nel deck, non quelli posseduti.
    private val cardIdToKeyMap by derivedStateOf {
        allCards.associate { it.id to getCardKey(it) }
    }

    /** Le carte solo-deck che stanno nel deck attualmente in modifica. */
    private val deckOnlyCardsInDeck: List<PokemonCard> by derivedStateOf {
        selectedCardsIds.distinct()
            .mapNotNull { allCardsById[it] }
            .filter { it.deckOnly }
    }

    /**
     * Quello che il selettore carte puo' offrire a questo deck: la collezione,
     * piu' le carte solo-deck di questo deck (senza, sparirebbero dalla griglia
     * appena aggiunte).
     */
    val deckUsableCards: List<PokemonCard> by derivedStateOf {
        val extra = deckOnlyCardsInDeck
        if (extra.isEmpty()) ownedCards else ownedCards + extra
    }

    // I due indici sono separati di proposito, come su Android: quello della
    // collezione e' il pesante e cambia solo con la collezione.

    /** Documenti posseduti per chiave carta. */
    private val ownedDocsByKey by derivedStateOf {
        ownedCards.groupBy { getCardKey(it) }
    }

    /** Documenti solo-deck del deck in modifica, per chiave carta. */
    private val deckOnlyDocsByKey by derivedStateOf {
        deckOnlyCardsInDeck.groupBy { getCardKey(it) }
    }

    /** Copie possedute per chiave carta. */
    private val ownedQuantitiesByKey by derivedStateOf {
        ownedCards.groupingBy { getCardKey(it) }
            .fold(0) { acc, card -> acc + card.quantity }
    }

    /** Copie solo-deck per chiave carta. */
    private val deckOnlyQuantitiesByKey by derivedStateOf {
        deckOnlyCardsInDeck.groupingBy { getCardKey(it) }
            .fold(0) { acc, card -> acc + card.quantity }
    }

    // Copie di ogni carta nel deck in modifica.
    private val deckQuantitiesByKey by derivedStateOf {
        selectedCardsIds.mapNotNull { cardIdToKeyMap[it] }
            .groupingBy { it }
            .eachCount()
    }

    init {
        reload()
        viewModelScope.launch { isPremium = premium.isPremium() }
        viewModelScope.launch { competitive.changes.drop(1).collect { reload() } }
        viewModelScope.launch { collection.changes.drop(1).collect { reload() } }
    }

    private fun reload() {
        viewModelScope.launch {
            decks = load { competitive.decks() } ?: decks
            isLoading = false
        }
        viewModelScope.launch {
            val cards = load { collection.loadIncludingDeckOnly() } ?: return@launch
            allCards = cards.sortedWith(
                compareBy<PokemonCard> {
                    when (classifyCard(it)) {
                        "Pokémon" -> 0
                        "Trainer" -> 1
                        "Energy" -> 2
                        else -> 3
                    }
                }.thenBy { it.name }
            )
            // Le carte appena create sono arrivate: l'analisi le puo' contare.
            if (selectedCardsIds.isNotEmpty()) analyzeDeck()
        }
    }

    private suspend fun <T> load(block: suspend () -> T): T? = try {
        block()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        null
    }

    fun getCardKey(card: PokemonCard): String =
        card.apiCardId.ifEmpty { "${card.name}-${card.set}-${card.cardNumber}-${card.variant}" }

    fun getQuantityInDeck(card: PokemonCard): Int {
        return deckQuantitiesByKey[getCardKey(card)] ?: 0
    }

    /** Copie che questo deck puo' usare: possedute, piu' le sue solo-deck. */
    fun getTotalOwnedQuantity(card: PokemonCard): Int {
        val key = getCardKey(card)
        return (ownedQuantitiesByKey[key] ?: 0) + (deckOnlyQuantitiesByKey[key] ?: 0)
    }

    fun classifyCard(card: PokemonCard): String = CardClassifier.classify(card)

    private fun isEnergy(card: PokemonCard): Boolean = classifyCard(card) == "Energy"

    // ── Premium: gli stessi limiti di PremiumManager ────────────────────────

    fun canCreateDeck(): Boolean =
        isPremium != false || decks.size < PremiumRepository.FREE_DECK_LIMIT

    /** Esportare la decklist e' solo Premium, come su Android. */
    fun canExportDecklist(): Boolean = isPremium == true

    // ── Editor ──────────────────────────────────────────────────────────────

    fun addCardToDeck(card: PokemonCard) {
        val key = getCardKey(card)
        val inDeckCount = getQuantityInDeck(card)
        val totalOwned = getTotalOwnedQuantity(card)

        if (inDeckCount >= totalOwned) {
            validationError = "Hai solo $totalOwned copie di questa carta."
            return
        }

        if (selectedCardsIds.size >= 60) {
            validationError = "Limite massimo di 60 carte raggiunto."
            return
        }

        if (!isEnergy(card)) {
            val sameNameCount = selectedCardsIds.count { id ->
                allCardsById[id]?.name == card.name
            }
            if (sameNameCount >= 4) {
                validationError = "Massimo 4 copie di ${card.name}."
                return
            }
        }

        val availableId = (ownedDocsByKey[key].orEmpty() + deckOnlyDocsByKey[key].orEmpty())
            .firstOrNull { doc ->
                val docInDeckCount = selectedCardsIds.count { it == doc.id }
                docInDeckCount < doc.quantity
            }?.id

        if (availableId != null) {
            selectedCardsIds = selectedCardsIds + availableId
            syncCoverImagesWithSelectedCards()
            validationError = null
            analyzeDeck()
        }
    }

    /**
     * Lo stato prima dell'ultima rimozione, per l'annulla: si rimette com'era
     * invece di riaggiungere la carta passando di nuovo dai controlli.
     */
    private data class DeckRemovalUndo(
        val cardIds: List<String>,
        val coverUrls: List<String>
    )

    private var lastRemoval by mutableStateOf<DeckRemovalUndo?>(null)

    /** Toglie una copia e restituisce il nome della carta tolta, o null. */
    fun removeCardFromDeck(card: PokemonCard): String? {
        val key = getCardKey(card)
        val idToRemove = selectedCardsIds.findLast { id ->
            cardIdToKeyMap[id] == key
        } ?: return null

        lastRemoval = DeckRemovalUndo(
            cardIds = selectedCardsIds,
            coverUrls = coverImageUrls
        )

        selectedCardsIds = selectedCardsIds - idToRemove
        syncCoverImagesWithSelectedCards()
        validationError = null
        analyzeDeck()
        return card.name
    }

    /** Rimette il deck com'era prima dell'ultima rimozione. */
    fun undoLastRemoval() {
        val snapshot = lastRemoval ?: return
        lastRemoval = null

        selectedCardsIds = snapshot.cardIds
        coverImageUrls = snapshot.coverUrls
        coverImageUrl = snapshot.coverUrls.firstOrNull().orEmpty()
        validationError = null
        analyzeDeck()
    }

    private fun analyzeDeck() {
        currentAnalysis = DeckAnalyzer.analyze(selectedCardsIds.mapNotNull { allCardsById[it] }, ::classifyCard)
    }

    fun prepareEdit(deck: Deck) {
        resetNewDeckState()
        editingDeckId = deck.id
        newDeckName = deck.name
        selectedCardsIds = deck.cards
        coverImageUrls = deck.displayCoverImageUrls()
        coverImageUrl = coverImageUrls.firstOrNull().orEmpty()
        // Riaprendo un deck di prova, le carte aggiunte adesso seguono la
        // stessa regola di quelle gia' dentro: nessuna sorpresa in collezione.
        deckCardSource = if (deck.deckOnly) DeckCardSource.DECK_ONLY else DeckCardSource.COLLECTION
        analyzeDeck()
    }

    fun saveDeck(onSuccess: () -> Unit) {
        if (newDeckName.isBlank()) {
            validationError = "Inserisci un nome per il deck."
            return
        }
        if (selectedCardsIds.isEmpty()) {
            validationError = "Seleziona almeno una carta."
            return
        }

        isSaving = true
        val mainTypes = currentAnalysis.typesCount.entries.sortedByDescending { it.value }.take(2).map { it.key }

        val deck = Deck(
            id = editingDeckId ?: "",
            name = newDeckName,
            cards = selectedCardsIds,
            mainTypes = mainTypes,
            averageHp = currentAnalysis.averageHp,
            totalCards = selectedCardsIds.size,
            recommendedEnergy = currentAnalysis.recommendedEnergy,
            coverImageUrl = coverImageUrls.firstOrNull().orEmpty(),
            coverImageUrls = coverImageUrls.take(2),
            // Non l'intenzione ma il fatto: un deck e' "di prova" se dentro ci
            // sono davvero carte che l'utente non ha.
            deckOnly = editingDeckHasDeckOnlyCards
        )

        viewModelScope.launch {
            try {
                competitive.saveDeck(deck)
                isSaving = false
                resetNewDeckState()
                onSuccess()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                isSaving = false
                validationError = "Errore database: ${e.message}"
            }
        }
    }

    /**
     * Via il deck, e con lui le sue carte solo-deck che nessun altro deck usa:
     * non sono in collezione, quindi nessun'altra schermata le mostrerebbe.
     */
    fun deleteDeck(deckId: String) {
        val deck = decks.find { it.id == deckId }
        viewModelScope.launch {
            load { competitive.deleteDeck(deckId) } ?: return@launch
            if (deck == null) return@launch
            val idsStillInUse = decks.filter { it.id != deckId }.flatMapTo(mutableSetOf()) { it.cards }
            val orphans = deck.cards.distinct()
                .filter { id -> id !in idsStillInUse && allCardsById[id]?.deckOnly == true }
            deleteDeckOnlyCards(orphans)
        }
    }

    /**
     * L'utente ha buttato via il deck che stava costruendo: le carte solo-deck
     * create per lui, se nessun deck salvato le usa, se ne vanno con lui.
     */
    fun discardEditingDeck() {
        val idsInSavedDecks = decks.flatMapTo(mutableSetOf()) { it.cards }
        val orphans = (sessionDeckOnlyCardIds + deckOnlyCardsInDeck.map { it.id })
            .filter { it !in idsInSavedDecks }

        resetNewDeckState()

        if (orphans.isEmpty()) return
        viewModelScope.launch { deleteDeckOnlyCards(orphans) }
    }

    private suspend fun deleteDeckOnlyCards(ids: List<String>) {
        if (ids.isEmpty()) return
        load { competitive.deleteDeckOnlyCards(ids) }
        collection.notifyChanged()
    }

    fun resetNewDeckState() {
        editingDeckId = null
        newDeckName = ""
        selectedCardsIds = emptyList()
        coverImageUrl = ""
        coverImageUrls = emptyList()
        isImportReviewMode = false
        importPlaceholderNames = emptyList()
        deckCardSource = DeckCardSource.COLLECTION
        isImportSourceChoicePending = false
        sessionDeckOnlyCardIds = emptySet()
        // Un annulla che risalisse a un deck precedente rimetterebbe dentro le sue carte.
        lastRemoval = null
        currentAnalysis = DeckAnalysis()
        validationError = null
    }

    fun duplicateDeck(deck: Deck) {
        viewModelScope.launch {
            load { competitive.saveDeck(deck.copy(id = "", name = "${deck.name} (Copia)")) }
        }
    }

    fun clearError() {
        validationError = null
    }

    fun exitImportReviewMode() {
        isImportReviewMode = false
    }

    fun toggleCoverCard(url: String) {
        if (url.isBlank()) return

        coverImageUrls = if (coverImageUrls.contains(url)) {
            coverImageUrls - url
        } else {
            (coverImageUrls + url).distinct().take(2)
        }

        syncCoverImagesWithSelectedCards()
    }

    private fun syncCoverImagesWithSelectedCards() {
        if (selectedCardsIds.isEmpty()) {
            coverImageUrls = emptyList()
            coverImageUrl = ""
            return
        }
        // Le copertine sono sprite: una che punta a un Pokemon non piu' nel
        // mazzo non si toglie qui, la ignora chi disegna (vedi Android).
        coverImageUrls = coverImageUrls.take(2)
        coverImageUrl = coverImageUrls.firstOrNull().orEmpty()
    }

    // ── Cerca nei set ───────────────────────────────────────────────────────

    /**
     * searchItalianCardsByName su Android: il catalogo italiano, gia' sul
     * telefono. [targetSetId] serve all'import (prossimo giro).
     */
    fun searchCardsInSets(query: String, targetSetId: String? = null) {
        if (query.isBlank()) {
            tcgSearchResults = emptyList()
            tcgSearchError = null
            isSearchingCards = false
            return
        }
        isSearchingCards = true
        tcgSearchError = null
        viewModelScope.launch {
            try {
                catalog.ensureExpansions()
                val names = expansionNames()
                val cards = catalog.search(query)
                    .filter { targetSetId == null || it.espansioneId.equals(targetSetId, ignoreCase = true) }
                    .mapNotNull { it.toTcgCard(names) }
                tcgSearchResults = cards.take(20)
                if (cards.isEmpty()) tcgSearchError = if (query.length >= 2)
                    "Nessuna carta trovata per \"$query\"" else null
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                tcgSearchError = "Errore durante la ricerca"
                tcgSearchResults = emptyList()
            }
            isSearchingCards = false
        }
    }

    fun clearTcgSearch() {
        tcgSearchResults = emptyList()
        tcgSearchError = null
        isSearchingCards = false
    }

    /**
     * Aggiunge al deck una carta trovata nei set, rispettando [deckCardSource]:
     * in collezione o confinata nel deck, a seconda del deck che si costruisce.
     */
    fun addTcgCardToDeck(card: TcgCard, qty: Int, onComplete: () -> Unit = {}) {
        val deckOnly = deckCardSource == DeckCardSource.DECK_ONLY
        // Il tetto va controllato prima di scrivere: a mazzo pieno la carta
        // non deve finire in collezione per poi restare fuori dal deck.
        val room = 60 - selectedCardsIds.size
        if (room <= 0) {
            validationError = "Limite massimo di 60 carte raggiunto."
            onComplete()
            return
        }
        val qtyToAdd = qty.coerceAtMost(room)
        viewModelScope.launch {
            try {
                val expansionId = card.source.espansioneId
                // Una carta solo-deck non vale niente: niente prezzo da cercare.
                val price = if (deckOnly) null else load { catalog.expansionCards(expansionId).priceOf(card.source) }
                val docId = writer.addForDeck(
                    card = card.source,
                    expansionName = card.set?.name ?: expansionId.uppercase(),
                    price = price,
                    quantity = qtyToAdd,
                    deckOnly = deckOnly,
                )
                if (deckOnly) sessionDeckOnlyCardIds = sessionDeckOnlyCardIds + docId
                selectedCardsIds = (selectedCardsIds + List(qtyToAdd) { docId }).take(60)
                analyzeDeck()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                validationError = "Errore database: ${e.message}"
            }
            onComplete()
        }
    }

    private companion object {
        val NON_ALNUM = Regex("[^a-z0-9]+")
    }

    private fun expansionNames(): Map<String, String> =
        (catalog.expansions.value as? ExpansionsState.Ready)?.expansions
            ?.associate { it.id.lowercase() to it.name }
            .orEmpty()

    private fun com.emabuia.pokevault.data.Card.toTcgCard(names: Map<String, String>): TcgCard? {
        val id = italianId() ?: return null
        val expansionId = espansioneId.trim().lowercase()
        return TcgCard(
            id = id,
            name = nome,
            number = number.orEmpty(),
            images = TcgImages(imageUrl(WORKER_BASE_URL + "/", size = "low")?.let { "$it&itv=r2v3" }.orEmpty()),
            set = TcgSet(id = "ita:$expansionId", name = names[expansionId] ?: expansionId.uppercase()),
            rarity = rarity,
            source = this,
        )
    }

    // ── Import ──────────────────────────────────────────────────────────────

    data class ImportResult(
        val matched: Int,
        val missing: Int,
        val missingCards: List<String>,
        val setMismatchWarnings: List<String> = emptyList(),
        val missingMetaDeckCards: List<MetaDeckCard> = emptyList(),
        val totalRequested: Int
    )

    private data class OwnedMatch(
        val cards: List<PokemonCard>,
        val usedFallbackSet: Boolean
    )

    var importResult by mutableStateOf<ImportResult?>(null)
        private set

    /**
     * L'import ha trovato delle carte mancanti e aspetta che l'utente dica
     * cosa farne. Finche' e' true, al posto del risultato si mostra la scelta.
     */
    var isImportSourceChoicePending by mutableStateOf(false)
        private set

    var isAddingMissingCards by mutableStateOf(false)
        private set

    /** Importa da testo (formato PTCG standard): matcha le carte possedute e pre-popola il deck. */
    fun importFromText(text: String): ImportResult {
        resetNewDeckState()
        isImportReviewMode = true

        val parsed = DeckImportParser.parse(text)
        if (parsed.deckName != null) {
            newDeckName = parsed.deckName
        }

        return matchAndPopulate(parsed.cards.map { card ->
            MetaDeckCard(
                name = card.name,
                set = card.set,
                number = card.number,
                qty = card.qty,
                type = card.type
            )
        })
    }

    /** Importa da un MetaDeck (dalle schede Meta Deck e Win Tournament). */
    fun importFromMetaDeck(metaDeck: MetaDeck): ImportResult {
        resetNewDeckState()
        isImportReviewMode = true
        newDeckName = metaDeck.archetype ?: metaDeck.player ?: "Deck Importato"

        return matchAndPopulate(metaDeck.cards)
    }

    /** Matcha una lista di carte con quelle possedute: nome + set + numero, poi ripieghi. */
    private fun matchAndPopulate(cards: List<MetaDeckCard>): ImportResult {
        val idsToAdd = mutableListOf<String>()
        val missingCards = mutableListOf<String>()
        val setMismatchWarnings = mutableListOf<String>()
        val missingMetaDeckCards = mutableListOf<MetaDeckCard>()
        var totalRequested = 0

        for (card in cards) {
            totalRequested += card.qty

            val matched = findOwnedCards(card.name, card.set, card.number)

            if (matched.cards.isEmpty()) {
                missingCards.add("${card.qty}x ${card.name}")
                missingMetaDeckCards.add(card)
                continue
            }

            if (matched.usedFallbackSet && !card.set.isNullOrBlank()) {
                val withNumber = if (card.number.isNullOrBlank()) card.name else "${card.name} ${card.number}"
                setMismatchWarnings.add("${card.qty}x $withNumber (${card.set})")
            }

            var remaining = card.qty
            for (ownedCard in matched.cards) {
                if (remaining <= 0) break
                val alreadyInDeck = idsToAdd.count { it == ownedCard.id }
                val available = ownedCard.quantity - alreadyInDeck
                val toAdd = minOf(remaining, available)
                repeat(toAdd) { idsToAdd.add(ownedCard.id) }
                remaining -= toAdd
            }

            if (remaining > 0) {
                missingCards.add("${remaining}x ${card.name} (possiedi meno copie)")
                missingMetaDeckCards.add(card.copy(qty = remaining))
            }
        }

        selectedCardsIds = idsToAdd.take(60)
        // Nessuna copertina d'ufficio: senza scelta decide headlineScore.
        analyzeDeck()

        val result = ImportResult(
            matched = idsToAdd.size,
            missing = missingCards.size,
            missingCards = missingCards,
            setMismatchWarnings = setMismatchWarnings.distinct(),
            missingMetaDeckCards = missingMetaDeckCards,
            totalRequested = totalRequested
        )
        importResult = result
        // Se non manca niente, non c'e' niente da chiedere.
        isImportSourceChoicePending = missingMetaDeckCards.isNotEmpty()
        return result
    }

    /** Le carte possedute che corrispondono a nome, set e numero; poi ripiego sul nome. */
    private fun findOwnedCards(name: String, set: String?, number: String?): OwnedMatch {
        val nameLower = name.lowercase().trim()

        // Le energie base si cercano per tipo, non per stampa.
        if (BasicEnergyResolver.isBasicEnergy(name)) {
            val ownedEnergies = ownedCards.filter { card ->
                BasicEnergyResolver.isSameBasicEnergy(card.name, name)
            }
            if (ownedEnergies.isNotEmpty()) {
                return OwnedMatch(
                    // Prima quelle con un'immagine: non i segnaposto di import vecchi.
                    cards = ownedEnergies.sortedByDescending { it.imageUrl.isNotBlank() },
                    usedFallbackSet = false
                )
            }
        }

        // 1. Match esatto: nome + set + numero
        if (set != null && number != null) {
            val exact = ownedCards.filter { card ->
                card.name.lowercase().trim() == nameLower &&
                    SetCodeMapper.matchesImportedSet(
                        importedSet = set,
                        cardSetName = card.set,
                        cardApiSetId = card.apiCardId.substringBefore("-"),
                        cardApiId = card.apiCardId
                    ) &&
                    card.cardNumber == number
            }
            if (exact.isNotEmpty()) return OwnedMatch(exact, usedFallbackSet = false)
        }

        // 2. Nome + numero, con preferenza per il set quando c'e'
        if (number != null) {
            val byNameAndNumber = ownedCards
                .filter { card ->
                    card.name.lowercase().trim() == nameLower &&
                        card.cardNumber == number
                }
                .sortedByDescending { card ->
                    SetCodeMapper.matchesImportedSet(
                        importedSet = set,
                        cardSetName = card.set,
                        cardApiSetId = card.apiCardId.substringBefore("-"),
                        cardApiId = card.apiCardId
                    )
                }

            if (byNameAndNumber.isNotEmpty()) {
                val hasSetMatch = set.isNullOrBlank() || SetCodeMapper.matchesImportedSet(
                    importedSet = set,
                    cardSetName = byNameAndNumber.first().set,
                    cardApiSetId = byNameAndNumber.first().apiCardId.substringBefore("-"),
                    cardApiId = byNameAndNumber.first().apiCardId
                )
                return OwnedMatch(byNameAndNumber, usedFallbackSet = !hasSetMatch)
            }
        }

        // 3. Nome esatto
        val byName = ownedCards.filter { card ->
            card.name.lowercase().trim() == nameLower
        }
        if (byName.isNotEmpty()) return OwnedMatch(byName, usedFallbackSet = !set.isNullOrBlank())

        // 4. Nome parziale
        val byPartial = ownedCards.filter { card ->
            card.name.lowercase().contains(nameLower) ||
                nameLower.contains(card.name.lowercase())
        }
        return OwnedMatch(byPartial, usedFallbackSet = byPartial.isNotEmpty() && !set.isNullOrBlank())
    }

    fun clearImportResult() {
        importResult = null
    }

    /**
     * L'utente ha scelto cosa fare delle carte mancanti: in collezione o solo
     * nel deck. In entrambi i casi il deck esce completo.
     */
    fun applyImportCardSource(source: DeckCardSource) {
        deckCardSource = source

        val missing = importResult?.missingMetaDeckCards.orEmpty()
        if (missing.isEmpty()) {
            isImportSourceChoicePending = false
            return
        }

        // La scelta resta a schermo finche' il lavoro non e' finito: e' li' che
        // vive l'indicatore di avanzamento.
        addMissingCardsToCollection(missing) {
            isImportSourceChoicePending = false
        }
    }

    /** "Continua senza": il deck resta con le sole carte gia' possedute. */
    fun skipMissingCards() {
        isImportSourceChoicePending = false
    }

    /**
     * Crea le carte mancanti e le aggiunge al deck. Ognuna si cerca nel
     * catalogo italiano; se non c'e', entra coi soli dati della decklist.
     * Finiscono in collezione o restano solo-deck secondo [deckCardSource].
     */
    fun addMissingCardsToCollection(missingCards: List<MetaDeckCard>, onComplete: () -> Unit = {}) {
        if (missingCards.isEmpty()) return
        isAddingMissingCards = true
        val deckOnly = deckCardSource == DeckCardSource.DECK_ONLY

        viewModelScope.launch {
            catalog.ensureExpansions()
            val names = expansionNames()
            // In sequenza: il catalogo e' gia' sul telefono, e cosi' l'ordine
            // delle carte nel deck resta quello della decklist.
            val newIds = mutableListOf<String>()
            val placeholders = mutableListOf<String>()
            for (card in missingCards) {
                val created = load { lookupAndCreateCard(card, deckOnly, names) } ?: continue
                val (docId, isPlaceholder) = created
                if (isPlaceholder) placeholders += card.name
                repeat(card.qty) { newIds.add(docId) }
                if (deckOnly) sessionDeckOnlyCardIds = sessionDeckOnlyCardIds + docId
            }
            importPlaceholderNames = placeholders.distinct()

            if (newIds.isNotEmpty()) {
                selectedCardsIds = (selectedCardsIds + newIds).take(60)
                analyzeDeck()
            }

            isAddingMissingCards = false
            // Il risultato resta a schermo, ma senza piu' mancanti.
            importResult = importResult?.copy(
                matched = selectedCardsIds.size,
                missing = 0,
                missingCards = emptyList(),
                missingMetaDeckCards = emptyList()
            )
            onComplete()
        }
    }

    /**
     * Crea il documento di una carta mancante e ne restituisce l'id, piu' se
     * e' un segnaposto senza immagine. Il prezzo solo per le carte possedute:
     * una solo-deck non vale niente.
     */
    private suspend fun lookupAndCreateCard(
        card: MetaDeckCard,
        deckOnly: Boolean,
        names: Map<String, String>,
    ): Pair<String, Boolean> {
        val found = resolveCatalogCard(card)
        if (found != null) {
            val expansionId = found.espansioneId.trim().lowercase()
            val price = if (deckOnly) null else load { catalog.expansionCards(found.espansioneId).priceOf(found) }
            // Senza PS e tipo il catalogo tira a indovinare la sezione: li'
            // decide la decklist, che la sezione la dice (vedi Android).
            val override = card.supertypeLabel()?.takeIf { found.ps.isNullOrBlank() && found.tipo.isNullOrBlank() }
            val id = writer.addForDeck(
                card = found,
                expansionName = names[expansionId] ?: expansionId.uppercase(),
                price = price,
                quantity = card.qty,
                deckOnly = deckOnly,
                supertypeOverride = override,
            )
            return id to false
        }

        // Ripiego: dati minimi dalla decklist.
        val supertype = card.supertypeLabel() ?: "Pokémon"
        val id = writer.addPlaceholder(
            PokemonCard(
                name = card.name,
                set = card.set ?: "",
                cardNumber = card.number ?: "",
                quantity = card.qty,
                estimatedValue = 0.0,
                supertype = supertype,
                hp = if (supertype == "Pokémon") 100 else 0,
                condition = "Near Mint",
                variant = "Normal",
                deckOnly = deckOnly
            )
        )
        return id to true
    }

    private fun MetaDeckCard.supertypeLabel(): String? = when (type.lowercase()) {
        "pokemon" -> "Pokémon"
        "trainer" -> "Trainer"
        "energy" -> "Energy"
        else -> null
    }

    /**
     * Tre tentativi prima di arrendersi a una carta senza immagine: set e
     * numero (col nome come conferma), l'energia base del tipo giusto, il
     * solo nome. Come resolveCatalogCard su Android.
     */
    private suspend fun resolveCatalogCard(card: MetaDeckCard): Card? {
        val isPokemon = card.type.equals("pokemon", ignoreCase = true)
        return catalog.findExactItalianCard(
            setCode = card.set,
            number = card.number,
            expectedName = card.name,
            requireNameMatch = isPokemon
        )
            ?: resolveBasicEnergyCard(card)
            ?: resolveByNameOnly(card)
    }

    /** Il set delle energie di PTCGL (SVE) in italiano non c'e': un'energia base del tipo giusto. */
    private suspend fun resolveBasicEnergyCard(card: MetaDeckCard): Card? {
        val energyName = BasicEnergyResolver.italianEnergyName(card.name) ?: return null
        return catalog.search(energyName).take(40).firstOrNull { candidate ->
            BasicEnergyResolver.isSameBasicEnergy(candidate.nome, energyName)
        }
    }

    /**
     * La carta cercata per nome, ignorando il set: il nome deve coincidere,
     * non somigliare ("Toucannon" non e' "Toucannon ex"). A parita' di nome
     * si preferisce lo stesso numero.
     */
    private suspend fun resolveByNameOnly(card: MetaDeckCard): Card? {
        val name = card.name.trim().takeIf { it.isNotBlank() } ?: return null
        val wantedName = normalizeCardNameForMatch(name)

        val candidates = catalog.search(name).take(40)
            .filter { candidate -> normalizeCardNameForMatch(candidate.nome) == wantedName }
            .takeIf { it.isNotEmpty() }
            ?: return null

        val wantedNumber = card.number?.trim()?.substringBefore('/')?.trimStart('0')

        return candidates.firstOrNull { candidate ->
            wantedNumber != null &&
                candidate.number.orEmpty().trim().trimStart('0').equals(wantedNumber, ignoreCase = true)
        } ?: candidates.first()
    }

    /** Minuscole, senza accenti e senza punteggiatura: per confrontare due nomi. */
    private fun normalizeCardNameForMatch(raw: String): String =
        IllustratorNames.stripDiacritics(raw.trim().lowercase())
            .replace(NON_ALNUM, " ")
            .trim()

    // ── Esporta ─────────────────────────────────────────────────────────────

    /** La decklist in formato PTCG Live, solo-deck comprese: e' la lista che si porta al tavolo. */
    fun buildPtcgDecklist(deck: Deck): String =
        DeckLists.ptcgDecklist(deck, allCardsById, ::getCardKey, ::classifyCard)
}

/** analyzeDeck di Android, fuori dal ViewModel per poterlo provare. */
internal object DeckAnalyzer {
    fun analyze(selectedCards: List<PokemonCard>, classify: (PokemonCard) -> String): DeckAnalysis {
        if (selectedCards.isEmpty()) return DeckAnalysis()

        // Solo i Pokemon: il tipo di una Trainer non esiste ("Colorless" di
        // ripiego), e contandole vincerebbe sempre Incolore.
        val typesCount = selectedCards
            .filter { classify(it) == CardClassifier.POKEMON }
            .flatMap { it.type.split(",").map { t -> t.trim() } }
            .filter { it.isNotEmpty() }
            .groupingBy { it }
            .eachCount()

        val supertypesCount = selectedCards.groupingBy { classify(it) }.eachCount()

        val avgHp = if (selectedCards.any { it.hp > 0 }) selectedCards.filter { it.hp > 0 }.map { it.hp }.average() else 0.0

        return DeckAnalysis(
            typesCount = typesCount,
            averageHp = avgHp,
            recommendedEnergy = emptyList(),
            synergies = emptyList(),
            commonWeaknesses = listOf("Variabile"),
            supertypesCount = supertypesCount
        )
    }
}

/** buildPtcgDecklist di Android, fuori dal ViewModel per poterlo provare. */
internal object DeckLists {
    private val NOT_CODE = Regex("[^A-Z0-9]")
    private val SET_CODE = Regex("^[A-Z]{2,5}\\d*$")

    fun ptcgDecklist(
        deck: Deck,
        idToCard: Map<String, PokemonCard>,
        cardKey: (PokemonCard) -> String,
        classify: (PokemonCard) -> String,
    ): String {
        val grouped = linkedMapOf<String, Pair<PokemonCard, Int>>()

        for (cardId in deck.cards) {
            val card = idToCard[cardId] ?: continue
            val key = cardKey(card)
            val existing = grouped[key]
            grouped[key] = if (existing == null) card to 1 else card to (existing.second + 1)
        }

        fun setCodeOrNull(card: PokemonCard): String? {
            val fromSet = card.set.uppercase().replace(NOT_CODE, "")
            val fromApi = card.apiCardId.substringBefore("-").uppercase().replace(NOT_CODE, "")
            return when {
                SET_CODE.matches(fromSet) -> fromSet
                SET_CODE.matches(fromApi) -> fromApi
                else -> null
            }
        }

        fun toDeckLine(card: PokemonCard, qty: Int): String {
            val setCode = setCodeOrNull(card)
            val number = card.cardNumber.trim()
            return if (!setCode.isNullOrBlank() && number.isNotBlank()) {
                "$qty ${card.name} $setCode $number"
            } else {
                "$qty ${card.name}"
            }
        }

        val pokemon = mutableListOf<String>()
        val trainer = mutableListOf<String>()
        val energy = mutableListOf<String>()

        grouped.values.forEach { (card, qty) ->
            when (classify(card)) {
                "Pokémon" -> pokemon += toDeckLine(card, qty)
                "Trainer" -> trainer += toDeckLine(card, qty)
                "Energy" -> energy += toDeckLine(card, qty)
                else -> pokemon += toDeckLine(card, qty)
            }
        }

        fun count(lines: List<String>) = lines.sumOf { line -> line.substringBefore(' ').toIntOrNull() ?: 0 }

        val builder = StringBuilder()
        builder.appendLine(deck.name.ifBlank { "Deck" })
        builder.appendLine()

        if (pokemon.isNotEmpty()) {
            builder.appendLine("Pokémon: ${count(pokemon)}")
            pokemon.forEach { builder.appendLine(it) }
            builder.appendLine()
        }
        if (trainer.isNotEmpty()) {
            builder.appendLine("Trainer: ${count(trainer)}")
            trainer.forEach { builder.appendLine(it) }
            builder.appendLine()
        }
        if (energy.isNotEmpty()) {
            builder.appendLine("Energy: ${count(energy)}")
            energy.forEach { builder.appendLine(it) }
        }

        return builder.toString().trimEnd()
    }
}
