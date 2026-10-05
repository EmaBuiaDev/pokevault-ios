package com.emabuia.pokevault.screens.album

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.CatalogRepository
import com.emabuia.pokevault.data.CollectionRepository
import com.emabuia.pokevault.data.CollectionWriter
import com.emabuia.pokevault.data.CollectorRepository
import com.emabuia.pokevault.data.Expansion
import com.emabuia.pokevault.data.ExpansionsState
import com.emabuia.pokevault.data.PremiumRepository
import com.emabuia.pokevault.data.model.CardOptions
import com.emabuia.pokevault.data.model.GoalAlbum
import com.emabuia.pokevault.data.model.GoalCriteriaType
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.util.ChaseCard
import com.emabuia.pokevault.util.ChaseRow
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/** Progresso calcolato on-the-fly, mai persistito. */
data class GoalProgress(
    val total: Int,
    val owned: Int,
    val missing: List<ChaseCard>,
    val duplicates: List<PokemonCard>,
    val percentage: Float
)

/**
 * GoalAlbumViewModel dell'app Android. Le carte obiettivo sono quelle del
 * catalogo italiano (ChaseCard al posto di TcgCard), e i chase si creano solo
 * per set, come su Android oggi.
 *
 * Il valore del criterio e' quello che scrive Android: "<id espansione>__ita"
 * (buildItalianSetId). I chase creati su Android per un set inglese si vedono e
 * contano, ma le loro carte non italiane non si sanno mostrare qui.
 */
class GoalAlbumViewModel(
    private val repository: CollectorRepository,
    private val collection: CollectionRepository,
    private val writer: CollectionWriter,
    private val catalog: CatalogRepository,
    private val premium: PremiumRepository,
) : ViewModel() {

    // ── State ──────────────────────────────────────────────────────────────

    var goalAlbums by mutableStateOf<List<GoalAlbum>>(emptyList())
        private set

    var ownedCards by mutableStateOf<List<PokemonCard>>(emptyList())
        private set

    // Parte a true: il loader viene avviato in init, quindi al primo frame
    // stiamo gia' caricando. Con false, un dettaglio lampeggiava "non trovato".
    var isLoading by mutableStateOf(true)
        private set

    var isSaving by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)

    var isPremium by mutableStateOf<Boolean?>(null)
        private set

    // ── Create form state ──────────────────────────────────────────────────

    var formName by mutableStateOf("")
    var formCriteriaType by mutableStateOf(GoalCriteriaType.SET)
    var formCriteriaValue by mutableStateOf("")

    var previewCards by mutableStateOf<List<ChaseCard>>(emptyList())
        private set
    var isPreviewLoading by mutableStateOf(false)
        private set

    /** Le espansioni italiane fra cui scegliere, dalla piu' recente. */
    var availableSets by mutableStateOf<List<Expansion>>(emptyList())
        private set

    init {
        viewModelScope.launch { loadGoalAlbums() }
        viewModelScope.launch { loadOwnedCards() }
        viewModelScope.launch { isPremium = premium.isPremium() }
        viewModelScope.launch { repository.changes.drop(1).collect { loadGoalAlbums() } }
        viewModelScope.launch { collection.changes.drop(1).collect { loadOwnedCards() } }
    }

    // ── Loaders ────────────────────────────────────────────────────────────

    private suspend fun loadGoalAlbums() {
        try {
            goalAlbums = repository.goalAlbums()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
        isLoading = false
    }

    private suspend fun loadOwnedCards() {
        try {
            ownedCards = collection.load()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }

    fun loadAvailableSets() {
        if (availableSets.isNotEmpty()) return
        viewModelScope.launch {
            runCatching { catalog.ensureExpansions() }
            availableSets = (catalog.expansions.value as? ExpansionsState.Ready)?.expansions.orEmpty()
        }
    }

    // ── Preview ────────────────────────────────────────────────────────────

    fun loadPreview() {
        if (formCriteriaValue.isBlank()) {
            previewCards = emptyList()
            return
        }
        viewModelScope.launch {
            isPreviewLoading = true
            previewCards = setCards(formCriteriaValue)
            isPreviewLoading = false
        }
    }

    // ── Progress ───────────────────────────────────────────────────────────

    fun getOwnedTargetCount(album: GoalAlbum): Int {
        if (album.targetCardApiIds.isEmpty()) return 0
        return album.targetCardApiIds.count { it in ownedApiIds }
    }

    /** Gli api id posseduti, indicizzati una volta sola. */
    private val ownedApiIds: Set<String> by derivedStateOf {
        ownedCards
            .asSequence()
            .filter { it.quantity >= 1 }
            .map { it.apiCardId.trim() }
            .toHashSet()
    }

    /** Le righe della lista chase: l'avanzamento e' gia' calcolato qui. */
    fun chaseRows(criteriaLabel: (GoalAlbum) -> String): List<ChaseRow> =
        goalAlbums.map { album ->
            ChaseRow(
                id = album.id,
                name = album.name,
                criteriaLabel = criteriaLabel(album),
                owned = getOwnedTargetCount(album),
                total = album.targetCardApiIds.size,
                createdAtSeconds = album.createdAt?.seconds ?: 0L
            )
        }

    /** Confronta targetCardApiIds con la collezione. Non fa rete. */
    fun getProgress(album: GoalAlbum, targetCards: List<ChaseCard>): GoalProgress {
        val ownedByApiId = ownedCards.groupBy { it.apiCardId.trim() }

        val owned = album.targetCardApiIds.count { apiId ->
            (ownedByApiId[apiId]?.sumOf { it.quantity } ?: 0) >= 1
        }
        val missing = targetCards.filter { tc ->
            (ownedByApiId[tc.id.trim()]?.sumOf { it.quantity } ?: 0) == 0
        }
        val duplicates = ownedCards.filter { pc ->
            pc.apiCardId.trim() in album.targetCardApiIds && pc.quantity > 1
        }
        val percentage = if (album.targetCardApiIds.isEmpty()) 0f
        else (owned.toFloat() / album.targetCardApiIds.size * 100f).coerceIn(0f, 100f)

        return GoalProgress(
            total = album.targetCardApiIds.size,
            owned = owned,
            missing = missing,
            duplicates = duplicates,
            percentage = percentage
        )
    }

    /**
     * Le carte obiettivo di un chase, col prezzo. Per un set italiano si leggono
     * le carte del set (veloce); per il resto si cercano gli id nel catalogo.
     * Le carte non italiane restano fuori: il chiamante le conta.
     */
    suspend fun targetCards(album: GoalAlbum): List<ChaseCard> {
        val setId = album.criteriaValue.takeIf { album.criteria == GoalCriteriaType.SET && it.endsWith(ITALIAN_SET_SUFFIX) }
        val wanted = album.targetCardApiIds.toSet()
        val cards = if (setId != null) {
            setCards(setId).filter { it.id in wanted }
        } else {
            val found = catalog.italianCardsById(wanted)
            found.values.map { card ->
                ChaseCard(card, runCatching { catalog.expansionCards(card.espansioneId).priceOf(card) }.getOrNull())
            }
        }
        // Nell'ordine in cui sono stati fissati alla creazione.
        val order = album.targetCardApiIds.withIndex().associate { (i, id) -> id to i }
        return cards.sortedBy { order[it.id] ?: Int.MAX_VALUE }
    }

    // ── Premium gate ───────────────────────────────────────────────────────

    /** canCreateGoalAlbum: gratis se ne tiene uno; se il server non risponde non si blocca. */
    fun canCreate(): Boolean = isPremium != false || goalAlbums.size < PremiumRepository.FREE_GOAL_ALBUM_LIMIT

    // ── CRUD ───────────────────────────────────────────────────────────────

    fun saveGoalAlbum(onSuccess: () -> Unit) {
        if (formName.isBlank() || formCriteriaValue.isBlank()) return
        viewModelScope.launch {
            isSaving = true
            try {
                val targets = setCards(formCriteriaValue).map { it.id }.filter { it.isNotBlank() }
                check(targets.isNotEmpty()) { "il set non ha carte" }
                repository.createGoalAlbum(
                    GoalAlbum(
                        name = formName.trim(),
                        criteriaType = GoalCriteriaType.SET.name,
                        criteriaValue = formCriteriaValue.trim(),
                        targetCardApiIds = targets,
                    )
                )
                resetForm()
                onSuccess()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                errorMessage = e.message
            }
            isSaving = false
        }
    }

    fun deleteGoalAlbum(albumId: String) {
        viewModelScope.launch { runCatching { repository.deleteGoalAlbum(albumId) } }
    }

    /**
     * Aggiunge una mancante alla collezione, come su Android: la prima stampa
     * possibile, una copia, Near Mint, italiano.
     */
    fun addMissingCardToCollection(card: ChaseCard, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val ok = try {
                val expansion = expansionOf(card.card.espansioneId)
                // Le stampe possibili come nel dettaglio carta: dalla rarita' e dalla data del set.
                val variants = CardOptions.getVariantsForCard(emptySet(), card.card.rarity, expansion?.releaseDate)
                writer.addFromCatalog(
                    card.card,
                    expansion?.name.orEmpty(),
                    card.price,
                    variants.firstOrNull() ?: "Holo",
                    1,
                    "Near Mint",
                    CardOptions.LANGUAGES.first(),
                )
                true
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                errorMessage = e.message
                false
            }
            onResult(ok)
        }
    }

    fun getGoalAlbumById(id: String): GoalAlbum? = goalAlbums.find { it.id == id }

    fun resetForm() {
        formName = ""
        formCriteriaType = GoalCriteriaType.SET
        formCriteriaValue = ""
        previewCards = emptyList()
    }

    // ── Private helpers ────────────────────────────────────────────────────

    /** Le carte di un set italiano ("me05__ita"), col prezzo minimo del set. */
    private suspend fun setCards(criteriaValue: String): List<ChaseCard> {
        val expansionId = criteriaValue.removeSuffix(ITALIAN_SET_SUFFIX)
        val content = try {
            catalog.expansionCards(expansionId)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return emptyList()
        }
        return content.cards.map { ChaseCard(it, content.priceOf(it)) }
    }

    private suspend fun expansionOf(expansionId: String): Expansion? {
        runCatching { catalog.ensureExpansions() }
        return (catalog.expansions.value as? ExpansionsState.Ready)?.expansions
            ?.firstOrNull { it.id.equals(expansionId, ignoreCase = true) }
    }

    companion object {
        /** ITALIAN_SET_SUFFIX su Android: l'id del set italiano nei chase. */
        const val ITALIAN_SET_SUFFIX = "__ita"

        fun italianSetId(expansionId: String): String = expansionId.trim().lowercase() + ITALIAN_SET_SUFFIX
    }
}
