package com.emabuia.pokevault.screens.competitive

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.CollectionRepository
import com.emabuia.pokevault.data.CompetitiveRepository
import com.emabuia.pokevault.data.PremiumRepository
import com.emabuia.pokevault.data.model.CardClassifier
import com.emabuia.pokevault.data.model.Deck
import com.emabuia.pokevault.data.model.PokemonCard
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Il Deck Lab: viewmodel/DeckLabViewModel.kt di Android, un pezzo alla volta.
 *
 * Per ora l'elenco dei mazzi, il dettaglio, elimina, duplica ed esporta.
 * L'editor (crea e modifica) e l'import arrivano nei prossimi giri.
 */
class DeckLabViewModel(
    private val competitive: CompetitiveRepository,
    private val collection: CollectionRepository,
    private val premium: PremiumRepository,
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

    /** null finche' il Worker non ha risposto: nel dubbio non si blocca niente. */
    var isPremium by mutableStateOf<Boolean?>(null)
        private set

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

    fun classifyCard(card: PokemonCard): String = CardClassifier.classify(card)

    /** L'id del catalogo di una carta del deck, per aprirne il dettaglio. */
    fun apiCardIdOf(cardId: String): String? = allCardsById[cardId]?.apiCardId?.takeIf { it.isNotBlank() }

    // ── Premium: gli stessi limiti di PremiumManager ────────────────────────

    fun canCreateDeck(): Boolean =
        isPremium != false || decks.size < PremiumRepository.FREE_DECK_LIMIT

    /** Esportare la decklist e' solo Premium, come su Android. */
    fun canExportDecklist(): Boolean = isPremium == true

    // ── Elimina e duplica ───────────────────────────────────────────────────

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
            if (orphans.isNotEmpty()) {
                load { competitive.deleteDeckOnlyCards(orphans) }
                collection.notifyChanged()
            }
        }
    }

    fun duplicateDeck(deck: Deck) {
        viewModelScope.launch {
            load { competitive.saveDeck(deck.copy(id = "", name = "${deck.name} (Copia)")) }
        }
    }

    // ── Esporta ─────────────────────────────────────────────────────────────

    /** La decklist in formato PTCG Live, solo-deck comprese: e' la lista che si porta al tavolo. */
    fun buildPtcgDecklist(deck: Deck): String =
        DeckLists.ptcgDecklist(deck, allCardsById, ::getCardKey, ::classifyCard)
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
