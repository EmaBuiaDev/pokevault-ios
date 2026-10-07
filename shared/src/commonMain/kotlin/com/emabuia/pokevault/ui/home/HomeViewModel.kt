package com.emabuia.pokevault.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.AuthRepository
import com.emabuia.pokevault.data.CollectionRepository
import com.emabuia.pokevault.data.PremiumRepository
import com.emabuia.pokevault.ui.theme.HomeSpritePreference
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.CardGroup
import com.emabuia.pokevault.util.CollectionBrowser
import com.emabuia.pokevault.util.CollectionSort
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Quante carte mostra la riga "Aggiunte di recente". */
private const val RECENT_CARDS = 10

/**
 * HomeViewModel dell'app Android: le ultime carte aggiunte, una tessera per
 * carta, dalla piu' nuova. Su iOS la Home si apre anche senza accesso: allora
 * non c'e' collezione da leggere e la riga non si mostra.
 */
class HomeViewModel(
    private val auth: AuthRepository,
    private val collection: CollectionRepository,
    private val premium: PremiumRepository,
    homeSprite: HomeSpritePreference,
) : ViewModel() {

    /** Lo sprite scelto nelle impostazioni (0 = casuale): vale solo per chi e' Premium. */
    val selectedHomeSpriteId: StateFlow<Int> = homeSprite.selectedId

    var isPremium by mutableStateOf(false)
        private set

    var recentGroups by mutableStateOf<List<CardGroup>>(emptyList())
        private set

    /** Se la collezione ha almeno una carta: serve a scegliere l'invito giusto. */
    var hasCards by mutableStateOf(false)
        private set

    var isLoading by mutableStateOf(true)
        private set

    /**
     * Se la cascata d'ingresso della griglia e' gia' stata giocata: sta nel
     * ViewModel perche' deve sopravvivere al ritorno sulla Home da un'altra tab.
     */
    var hasEnteredOnce by mutableStateOf(false)
        private set

    fun markEntered() {
        hasEnteredOnce = true
    }

    init {
        // Si rilegge a ogni accesso e a ogni scrittura sulla collezione.
        viewModelScope.launch {
            auth.session.collectLatest { session ->
                if (session == null) {
                    recentGroups = emptyList()
                    hasCards = false
                    isLoading = false
                    isPremium = false
                    return@collectLatest
                }
                // Nello scope della sessione: un cambio d'account annulla tutte e due.
                coroutineScope {
                    launch { isPremium = premium.isPremium() == true }
                    collection.changes.collectLatest { loadCards() }
                }
            }
        }
    }

    private suspend fun loadCards() {
        val cards = try {
            collection.load()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            isLoading = false
            return
        }
        val unknown = AppLocale.unknownExpansion
        recentGroups = withContext(Dispatchers.Default) {
            CollectionBrowser.sort(CollectionBrowser.group(cards, unknown), CollectionSort.NEWEST)
                .take(RECENT_CARDS)
        }
        hasCards = cards.isNotEmpty()
        isLoading = false
    }
}
