package com.emabuia.pokevault.screens.competitive

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.competitive.CompetitiveSummary
import com.emabuia.pokevault.data.competitive.MatchupAnalyzer
import com.emabuia.pokevault.data.CompetitiveRepository
import com.emabuia.pokevault.data.PremiumRepository
import com.emabuia.pokevault.data.model.Deck
import com.emabuia.pokevault.data.model.MatchLog
import com.emabuia.pokevault.data.model.Timestamp
import com.emabuia.pokevault.data.model.Tournament
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Su iOS i dati arrivano da CompetitiveRepository e non da tre listener
 * Firestore: si rilegge tutto a ogni scrittura. Le partite di un torneo sono
 * un filtro su [allMatches], non una query a parte.
 */
class CompetitiveLogViewModel(
    private val repository: CompetitiveRepository,
    private val premium: PremiumRepository,
) : ViewModel() {

    // ── Tornei ──
    var tournaments by mutableStateOf<List<Tournament>>(emptyList())
        private set

    // ── Partite del torneo corrente ──
    var tournamentMatches by mutableStateOf<List<MatchLog>>(emptyList())
        private set

    // ── Deck dell'utente ──
    var userDecks by mutableStateOf<List<Deck>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var isSaving by mutableStateOf(false)
        private set

    // ── Tournament form state ──
    var editingTournamentId by mutableStateOf<String?>(null)
    var tournamentLocation by mutableStateOf("")
    var tournamentDate by mutableStateOf<Timestamp>(Timestamp.now())
    var tournamentParticipants by mutableStateOf("")
    var tournamentFee by mutableStateOf("")
    var tournamentType by mutableStateOf("")
    var tournamentFormat by mutableStateOf("")
    var tournamentDeckName by mutableStateOf("")
    var tournamentDeckId by mutableStateOf("")

    // ── Match form state ──
    var editingMatchId by mutableStateOf<String?>(null)
    var matchTournamentId by mutableStateOf("")
    var matchRound by mutableStateOf("")
    var matchResult by mutableStateOf("")
    var matchOpponentName by mutableStateOf("")
    var matchOpponentDeck by mutableStateOf("")
    var matchNotes by mutableStateOf("")

    // ── Stats (calcolate sulle partite del torneo corrente) ──
    val wins: Int get() = tournamentMatches.count { it.result == "W" }
    val losses: Int get() = tournamentMatches.count { it.result == "L" }
    val ties: Int get() = tournamentMatches.count { it.result == "T" }
    val winRate: Float get() {
        val total = wins + losses + ties
        return if (total == 0) 0f else (wins.toFloat() / total) * 100f
    }

    // ── Stats globali (tutte le partite) ──
    var allMatches by mutableStateOf<List<MatchLog>>(emptyList())
        private set
    val globalWins: Int get() = allMatches.count { it.result == "W" }
    val globalLosses: Int get() = allMatches.count { it.result == "L" }
    val globalTies: Int get() = allMatches.count { it.result == "T" }
    val globalWinRate: Float get() {
        val total = globalWins + globalLosses + globalTies
        return if (total == 0) 0f else (globalWins.toFloat() / total) * 100f
    }

    /**
     * Tutto quello che si puo' dire sulle partite registrate.
     *
     * E' `derivedStateOf` e non una funzione: la schermata delle statistiche la
     * legge in piu' punti durante la stessa composizione, e ricalcolare
     * l'aggregazione a ogni lettura sarebbe lavoro buttato. Si aggiorna da sola
     * quando cambiano le partite o i tornei.
     */
    val summary: CompetitiveSummary by derivedStateOf {
        MatchupAnalyzer.analyze(allMatches, tournaments)
    }

    /**
     * I mazzi avversari gia' incontrati, dal piu' frequente.
     *
     * Servono a proporli al momento di registrare una partita: il campo e'
     * libero, e la stessa grafia scritta in tre modi diversi produce tre
     * matchup separati che non dicono niente.
     */
    val knownOpponentDecks: List<String> by derivedStateOf {
        MatchupAnalyzer.knownOpponentDecks(allMatches)
    }

    /** Il torneo di cui si guardano le partite, se c'e'. */
    private var currentTournamentId: String? = null

    /** null finche' il Worker non ha risposto: nel dubbio non si blocca niente. */
    var isPremium by mutableStateOf<Boolean?>(null)
        private set

    init {
        isLoading = true
        reload()
        viewModelScope.launch { isPremium = premium.isPremium() }
        viewModelScope.launch { repository.changes.drop(1).collect { reload() } }
    }

    private fun reload() {
        viewModelScope.launch {
            tournaments = load { repository.tournaments() } ?: tournaments
            isLoading = false
        }
        viewModelScope.launch { userDecks = load { repository.decks() } ?: userDecks }
        viewModelScope.launch {
            allMatches = load { repository.matchLogs() } ?: allMatches
            refreshTournamentMatches()
        }
    }

    /** Un errore lascia i dati che c'erano: come il .catch dei listener su Android. */
    private suspend fun <T> load(block: suspend () -> T): T? = try {
        block()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        null
    }

    fun loadMatchesForTournament(tournamentId: String) {
        currentTournamentId = tournamentId
        refreshTournamentMatches()
    }

    private fun refreshTournamentMatches() {
        val id = currentTournamentId ?: return
        tournamentMatches = allMatches.filter { it.tournamentId == id }.sortedBy { it.round }
    }

    /** canCreateTournament di PremiumManager: gratis se ne tiene uno. */
    fun canCreateTournament(): Boolean =
        isPremium != false || tournaments.size < PremiumRepository.FREE_TOURNAMENT_LIMIT

    // ── Tournament CRUD ──

    fun getTournamentById(tournamentId: String): Tournament? {
        return tournaments.find { it.id == tournamentId }
    }

    fun loadTournamentForEdit(tournament: Tournament) {
        editingTournamentId = tournament.id
        tournamentLocation = tournament.location
        tournamentDate = tournament.date ?: Timestamp.now()
        tournamentParticipants = if (tournament.participants > 0) tournament.participants.toString() else ""
        tournamentFee = if (tournament.registrationFee > 0) tournament.registrationFee.toString() else ""
        tournamentType = tournament.type
        tournamentFormat = tournament.format
        tournamentDeckName = tournament.deckName
        tournamentDeckId = tournament.deckId
    }

    fun saveTournament(onSuccess: () -> Unit = {}) {
        if (tournamentType.isBlank()) return
        viewModelScope.launch {
            isSaving = true
            val existing = editingTournamentId?.let { id -> tournaments.find { it.id == id } }
            val tournament = Tournament(
                id = editingTournamentId ?: "",
                location = tournamentLocation,
                date = tournamentDate,
                participants = tournamentParticipants.toIntOrNull() ?: 0,
                registrationFee = tournamentFee.toDoubleOrNull() ?: 0.0,
                type = tournamentType,
                format = tournamentFormat,
                deckName = tournamentDeckName,
                deckId = tournamentDeckId,
                createdAt = existing?.createdAt
            )
            load { repository.saveTournament(tournament) }
            isSaving = false
            resetTournamentForm()
            onSuccess()
        }
    }

    fun deleteTournament(tournamentId: String) {
        viewModelScope.launch {
            load { repository.deleteTournament(tournamentId) }
        }
    }

    fun resetTournamentForm() {
        editingTournamentId = null
        tournamentLocation = ""
        tournamentDate = Timestamp.now()
        tournamentParticipants = ""
        tournamentFee = ""
        tournamentType = ""
        tournamentFormat = ""
        tournamentDeckName = ""
        tournamentDeckId = ""
    }

    // ── Match CRUD ──

    fun getMatchById(matchId: String): MatchLog? {
        return tournamentMatches.find { it.id == matchId }
    }

    fun loadMatchForEdit(match: MatchLog) {
        editingMatchId = match.id
        matchTournamentId = match.tournamentId
        matchRound = if (match.round > 0) match.round.toString() else ""
        matchResult = match.result
        matchOpponentName = match.opponentName
        matchOpponentDeck = match.opponentDeck
        matchNotes = match.notes
    }

    fun saveMatch(onSuccess: () -> Unit = {}) {
        if (matchResult.isBlank() || matchTournamentId.isBlank()) return
        viewModelScope.launch {
            isSaving = true
            val existing = editingMatchId?.let { id -> tournamentMatches.find { it.id == id } }
            val match = MatchLog(
                id = editingMatchId ?: "",
                tournamentId = matchTournamentId,
                // Il round successivo si deriva dal massimo esistente, non da
                // size + 1: con un match cancellato in mezzo, size + 1 riusava
                // un numero gia' assegnato.
                round = matchRound.toIntOrNull()
                    ?: ((tournamentMatches.maxOfOrNull { it.round } ?: 0) + 1),
                result = matchResult,
                opponentName = matchOpponentName,
                opponentDeck = matchOpponentDeck,
                notes = matchNotes,
                createdAt = existing?.createdAt
            )
            load { repository.saveMatchLog(match) }
            isSaving = false
            resetMatchForm()
            onSuccess()
        }
    }

    fun deleteMatch(matchId: String) {
        viewModelScope.launch {
            load { repository.deleteMatchLog(matchId) }
        }
    }

    fun resetMatchForm() {
        editingMatchId = null
        matchRound = ""
        matchResult = ""
        matchOpponentName = ""
        matchOpponentDeck = ""
        matchNotes = ""
    }
}
