package com.emabuia.pokevault

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.emabuia.pokevault.data.Expansion
import com.emabuia.pokevault.screens.ComingSoonScreen
import com.emabuia.pokevault.screens.card.CardDetailScreen
import com.emabuia.pokevault.screens.card.CardByApiIdScreen
import com.emabuia.pokevault.screens.album.AlbumCollectionListScreen
import com.emabuia.pokevault.screens.album.AlbumDetailScreen
import com.emabuia.pokevault.screens.album.AlbumListScreen
import com.emabuia.pokevault.screens.album.ChaseListScreen
import com.emabuia.pokevault.screens.album.CreateAlbumScreen
import com.emabuia.pokevault.screens.album.CreateGoalAlbumScreen
import com.emabuia.pokevault.screens.album.GoalAlbumDetailScreen
import com.emabuia.pokevault.screens.auth.RequireLogin
import com.emabuia.pokevault.screens.collection.CollectionScreen
import com.emabuia.pokevault.screens.collection.CollectionCardDetailScreen
import com.emabuia.pokevault.screens.competitive.AddMatchScreen
import com.emabuia.pokevault.screens.competitive.AddTournamentScreen
import com.emabuia.pokevault.screens.competitive.CompetitiveHubScreen
import com.emabuia.pokevault.screens.competitive.HandSimulatorScreen
import com.emabuia.pokevault.screens.deck.DeckLabScreen
import com.emabuia.pokevault.screens.competitive.MatchLogScreen
import com.emabuia.pokevault.screens.competitive.TournamentDetailScreen
import com.emabuia.pokevault.util.PokemonSpriteResolver
import androidx.compose.runtime.LaunchedEffect
import com.emabuia.pokevault.screens.pokedex.SetsListScreen
import com.emabuia.pokevault.screens.pokedex.SetDetailScreen
import com.emabuia.pokevault.data.PokedexCatalog
import com.emabuia.pokevault.screens.graded.GradedCardsScreen
import com.emabuia.pokevault.screens.illustrator.IllustratorDetailScreen
import com.emabuia.pokevault.screens.illustrator.IllustratorListScreen
import com.emabuia.pokevault.screens.settings.SettingsScreen
import com.emabuia.pokevault.screens.stats.StatsScreen
import com.emabuia.pokevault.screens.wishlist.WishlistDetailScreen
import com.emabuia.pokevault.screens.wishlist.WishlistListScreen
import com.emabuia.pokevault.ui.home.HomeScreen
import com.emabuia.pokevault.ui.navigation.BottomTab
import com.emabuia.pokevault.ui.navigation.PokeVaultBottomBar
import com.emabuia.pokevault.ui.navigation.TradeRadarBarButton
import com.emabuia.pokevault.data.AuthRepository
import com.emabuia.pokevault.data.trade.TradeApi
import com.emabuia.pokevault.data.trade.TradeBadge
import com.emabuia.pokevault.data.trade.TradePrefs
import com.emabuia.pokevault.screens.trade.TradeRadarScreen
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.ui.theme.PokeVaultTheme
import com.emabuia.pokevault.ui.theme.ThemeMode
import com.emabuia.pokevault.ui.theme.ThemePreference
import org.koin.compose.koinInject
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import kotlinx.serialization.Serializable
import com.emabuia.pokevault.screens.scanner.ScannerScreen
import com.emabuia.pokevault.ui.navigation.ScannerFab
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

@Serializable
object HomeDestination

@Serializable
object CollectionDestination

@Serializable
object PokedexDestination

@Serializable
object StatsDestination

@Serializable
data class SetDetailDestination(val setId: String, val setName: String, val sourceMacro: String? = null)

@Serializable
data class CardDetailDestination(val expansionId: String, val cardId: String)

@Serializable
object WishlistDestination

@Serializable
data class WishlistDetailDestination(val wishlistId: String)

@Serializable
object SettingsDestination

@Serializable
object GradedDestination

@Serializable
object CollectorLabDestination

@Serializable
object AlbumListDestination

/** albumId null = album nuovo. */
@Serializable
data class CreateAlbumDestination(val albumId: String? = null)

@Serializable
data class AlbumDetailDestination(val albumId: String)

@Serializable
object ChaseListDestination

@Serializable
object CreateChaseDestination

@Serializable
data class ChaseDetailDestination(val goalAlbumId: String)

/** Una carta aperta dall'id di collezione, album o chase ("ita:me05:4"). */
@Serializable
data class CardByApiIdDestination(val apiCardId: String)

@Serializable
object IllustratorsDestination

@Serializable
data class IllustratorDetailDestination(val key: String)

@Serializable
object CompetitiveDestination

@Serializable
object MatchLogDestination

@Serializable
object HandSimulatorDestination

@Serializable
object DeckLabDestination

@Serializable
object ScannerDestination

@Serializable
object TradeRadarDestination

/** Una carta della collezione, con tutte le sue stampe: [key] e' la chiave della tessera. */
@Serializable
data class CollectionCardDestination(val key: String)

/** [tournamentId] null: torneo nuovo. */
@Serializable
data class AddTournamentDestination(val tournamentId: String? = null)

@Serializable
data class TournamentDetailDestination(val tournamentId: String)

/** [matchId] null: partita nuova. */
@Serializable
data class AddMatchDestination(val tournamentId: String, val matchId: String? = null)

/** Una sezione dell'app Android che su iOS non c'e' ancora. */
@Serializable
data class ComingSoonDestination(val title: String)

private val BottomTab.destination: Any
    get() = when (this) {
        BottomTab.HOME -> HomeDestination
        BottomTab.CARDS -> CollectionDestination
        BottomTab.POKEDEX -> PokedexDestination
        BottomTab.STATS -> StatsDestination
    }

private const val NOT_PORTED_YET = "Questa sezione c'e' sull'app Android e arrivera' presto anche qui."

@Composable
fun App() {
    val themeMode by koinInject<ThemePreference>().mode.collectAsState()
    val systemDark = isSystemInDarkTheme()
    // La tabella degli sprite si legge una volta, fuori dal thread principale:
    // le righe che la usano si ridisegnano da sole quando e' pronta.
    LaunchedEffect(Unit) { PokemonSpriteResolver.preload() }
    PokeVaultTheme(
        darkTheme = when (themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> systemDark
        },
    ) {
        Surface(color = AppColors.background) {
            val navController: NavHostController = rememberNavController()
            val backStackEntry by navController.currentBackStackEntryAsState()
            // La barra si vede solo sulle quattro sezioni, come su Android.
            val selectedTab = BottomTab.entries.firstOrNull { tab ->
                backStackEntry?.destination?.hierarchy?.any { it.hasRoute(tab.destination::class) } == true
            }
            val openExpansion = { expansion: Expansion ->
                navController.navigate(SetDetailDestination(PokedexCatalog.italianSetId(expansion.id), expansion.name, "ITA"))
            }

            // Il numero sul tasto TradeRadar: si rilegge quando l'app torna in primo
            // piano (dentro TradeRadar lo aggiorna il ViewModel). Senza accesso e' 0.
            val session by koinInject<AuthRepository>().session.collectAsState()
            val tradeApi = koinInject<TradeApi>()
            val tradePrefs = koinInject<TradePrefs>()
            val badgeScope = rememberCoroutineScope()
            LifecycleResumeEffect(session) {
                val job = badgeScope.launch {
                    if (session != null) TradeBadge.refresh(tradeApi, tradePrefs.read().appliedClosings) else TradeBadge.update(0)
                }
                onPauseOrDispose { job.cancel() }
            }

            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    NavHost(navController = navController, startDestination = HomeDestination) {
                        composable<HomeDestination> {
                            HomeScreen(
                                onMenuClick = { routeKey ->
                                    when (routeKey) {
                                        "my_cards" -> navController.selectTab(BottomTab.CARDS)
                                        "statistics" -> navController.selectTab(BottomTab.STATS)
                                        "pokedex" -> navController.selectTab(BottomTab.POKEDEX)
                                        "wishlist" -> navController.navigate(WishlistDestination)
                                        "collector_lab" -> navController.navigate(CollectorLabDestination)
                                        "graded" -> navController.navigate(GradedDestination)
                                        "competitive" -> navController.navigate(CompetitiveDestination)
                                        "trade_radar" -> navController.navigate(TradeRadarDestination) { launchSingleTop = true }
                                        else -> navController.navigate(ComingSoonDestination(menuTitle(routeKey)))
                                    }
                                },
                                onExpansionClick = openExpansion,
                                onSearchClick = { navController.selectTab(BottomTab.POKEDEX) },
                                onSettingsClick = { navController.navigate(SettingsDestination) },
                            )
                        }
                        composable<CollectionDestination> {
                            RequireLogin { session, onLogout ->
                                CollectionScreen(
                                    session = session,
                                    onLogout = onLogout,
                                    onAddCard = { navController.selectTab(BottomTab.POKEDEX) },
                                    onCardClick = { key -> navController.navigate(CollectionCardDestination(key)) },
                                )
                            }
                        }
                        composable<PokedexDestination> {
                            SetsListScreen(
                                onBack = { navController.popBackStack() },
                                // Come su Android: il nome provvisorio e' l'id, il titolo vero arriva col set.
                                onSetClick = { setId, macro -> navController.navigate(SetDetailDestination(setId, setId, macro)) },
                                onIllustratorClick = { key -> navController.navigate(IllustratorDetailDestination(key)) },
                            )
                        }
                        composable<SetDetailDestination> { entry ->
                            val destination = entry.toRoute<SetDetailDestination>()
                            SetDetailScreen(
                                setId = destination.setId,
                                setName = destination.setName,
                                sourceMacro = destination.sourceMacro,
                                onBack = { navController.popBackStack() },
                                // Su iOS il Premium arriva con StoreKit (dopo l'account Apple a pagamento).
                                onPremiumRequired = {},
                                onIllustratorClick = { key -> navController.navigate(IllustratorDetailDestination(key)) },
                            )
                        }
                        composable<StatsDestination> {
                            RequireLogin { _, _ -> StatsScreen() }
                        }
                        composable<CardDetailDestination> { entry ->
                            val destination = entry.toRoute<CardDetailDestination>()
                            CardDetailScreen(
                                expansionId = destination.expansionId,
                                cardId = destination.cardId,
                                onBack = { navController.popBackStack() },
                                // Le frecce sostituiscono la carta, non la impilano: Indietro
                                // torna alla griglia, non a ogni carta sfogliata.
                                onOpenCard = { expansionId, cardId ->
                                    navController.popBackStack()
                                    navController.navigate(CardDetailDestination(expansionId, cardId))
                                },
                                onIllustratorClick = { key -> navController.navigate(IllustratorDetailDestination(key)) },
                            )
                        }
                        composable<CollectorLabDestination> {
                            // Album e chase stanno sull'account: come Carte e Stats, serve l'accesso.
                            RequireLogin { _, _ ->
                                AlbumListScreen(
                                    onBack = { navController.popBackStack() },
                                    onCreateAlbum = { id -> navController.navigate(CreateAlbumDestination(id)) },
                                    onAlbumClick = { id -> navController.navigate(AlbumDetailDestination(id)) },
                                    onOpenAlbumList = { navController.navigate(AlbumListDestination) },
                                    onOpenChaseList = { navController.navigate(ChaseListDestination) },
                                    onOpenIllustrators = { navController.navigate(IllustratorsDestination) },
                                    onIllustratorClick = { key -> navController.navigate(IllustratorDetailDestination(key)) },
                                    onCreateChase = { navController.navigate(CreateChaseDestination) },
                                    onChaseClick = { id -> navController.navigate(ChaseDetailDestination(id)) },
                                )
                            }
                        }
                        composable<AlbumListDestination> {
                            AlbumCollectionListScreen(
                                onBack = { navController.popBackStack() },
                                onAlbumClick = { id -> navController.navigate(AlbumDetailDestination(id)) },
                                onCreateAlbum = { id -> navController.navigate(CreateAlbumDestination(id)) },
                                onPremiumRequired = {},
                            )
                        }
                        composable<CreateAlbumDestination> { entry ->
                            CreateAlbumScreen(
                                onBack = { navController.popBackStack() },
                                editAlbumId = entry.toRoute<CreateAlbumDestination>().albumId,
                            )
                        }
                        composable<AlbumDetailDestination> { entry ->
                            AlbumDetailScreen(
                                albumId = entry.toRoute<AlbumDetailDestination>().albumId,
                                onBack = { navController.popBackStack() },
                                onCardClick = { card -> navController.navigate(CardByApiIdDestination(card.apiCardId)) },
                            )
                        }
                        composable<ChaseListDestination> {
                            ChaseListScreen(
                                onBack = { navController.popBackStack() },
                                onCreateChase = { navController.navigate(CreateChaseDestination) },
                                onChaseClick = { id -> navController.navigate(ChaseDetailDestination(id)) },
                                onPremiumRequired = {},
                            )
                        }
                        composable<CreateChaseDestination> {
                            CreateGoalAlbumScreen(
                                onBack = { navController.popBackStack() },
                                onSaved = { navController.popBackStack() },
                                onPremiumRequired = {},
                            )
                        }
                        composable<ChaseDetailDestination> { entry ->
                            GoalAlbumDetailScreen(
                                goalAlbumId = entry.toRoute<ChaseDetailDestination>().goalAlbumId,
                                onBack = { navController.popBackStack() },
                            )
                        }
                        composable<CardByApiIdDestination> { entry ->
                            CardByApiIdScreen(
                                apiCardId = entry.toRoute<CardByApiIdDestination>().apiCardId,
                                onBack = { navController.popBackStack() },
                                onOpenCard = { expansionId, cardId ->
                                    navController.popBackStack()
                                    navController.navigate(CardDetailDestination(expansionId, cardId))
                                },
                                onIllustratorClick = { key -> navController.navigate(IllustratorDetailDestination(key)) },
                            )
                        }
                        composable<GradedDestination> {
                            RequireLogin { _, _ -> GradedCardsScreen(onBack = { navController.popBackStack() }) }
                        }
                        composable<IllustratorsDestination> {
                            IllustratorListScreen(
                                onBack = { navController.popBackStack() },
                                onIllustratorClick = { key -> navController.navigate(IllustratorDetailDestination(key)) },
                            )
                        }
                        composable<IllustratorDetailDestination> { entry ->
                            IllustratorDetailScreen(
                                illustratorKey = entry.toRoute<IllustratorDetailDestination>().key,
                                onBack = { navController.popBackStack() },
                                onCardClick = { card -> navController.navigate(CardDetailDestination(card.espansioneId, card.cardId)) },
                            )
                        }
                        composable<SettingsDestination> {
                            SettingsScreen(onBack = { navController.popBackStack() })
                        }
                        composable<WishlistDestination> {
                            RequireLogin { _, _ ->
                                WishlistListScreen(
                                    onBack = { navController.popBackStack() },
                                    onPremiumRequired = {},
                                    onWishlistClick = { id -> navController.navigate(WishlistDetailDestination(id)) },
                                )
                            }
                        }
                        composable<WishlistDetailDestination> { entry ->
                            val destination = entry.toRoute<WishlistDetailDestination>()
                            RequireLogin { _, _ ->
                                WishlistDetailScreen(
                                    wishlistId = destination.wishlistId,
                                    onBack = { navController.popBackStack() },
                                )
                            }
                        }
                        composable<CompetitiveDestination> {
                            // Mazzi, tornei e partite stanno sull'account: serve l'accesso.
                            RequireLogin { _, _ ->
                                CompetitiveHubScreen(
                                    onBack = { navController.popBackStack() },
                                    onNavigateToDeckLab = { navController.navigate(DeckLabDestination) },
                                    onNavigateToMatchLog = { navController.navigate(MatchLogDestination) },
                                    onNavigateToHandSimulator = { navController.navigate(HandSimulatorDestination) },
                                )
                            }
                        }
                        composable<DeckLabDestination> {
                            DeckLabScreen(
                                onBack = { navController.popBackStack() },
                                onCardClick = { apiCardId -> navController.navigate(CardByApiIdDestination(apiCardId)) },
                            )
                        }
                        composable<HandSimulatorDestination> {
                            HandSimulatorScreen(
                                onBack = { navController.popBackStack() },
                                onNavigateToPremium = {},
                            )
                        }
                        composable<MatchLogDestination> {
                            MatchLogScreen(
                                onBack = { navController.popBackStack() },
                                onAddTournament = { id -> navController.navigate(AddTournamentDestination(id)) },
                                onTournamentClick = { id -> navController.navigate(TournamentDetailDestination(id)) },
                            )
                        }
                        composable<AddTournamentDestination> { entry ->
                            AddTournamentScreen(
                                onBack = { navController.popBackStack() },
                                editTournamentId = entry.toRoute<AddTournamentDestination>().tournamentId,
                            )
                        }
                        composable<TournamentDetailDestination> { entry ->
                            val tournamentId = entry.toRoute<TournamentDetailDestination>().tournamentId
                            TournamentDetailScreen(
                                tournamentId = tournamentId,
                                onBack = { navController.popBackStack() },
                                onAddMatch = { id -> navController.navigate(AddMatchDestination(id)) },
                                onEditMatch = { id, matchId -> navController.navigate(AddMatchDestination(id, matchId)) },
                            )
                        }
                        composable<AddMatchDestination> { entry ->
                            val destination = entry.toRoute<AddMatchDestination>()
                            AddMatchScreen(
                                onBack = { navController.popBackStack() },
                                tournamentId = destination.tournamentId,
                                editMatchId = destination.matchId,
                            )
                        }
                        composable<ScannerDestination> {
                            // Le carte scansionate vanno in collezione: serve l'accesso.
                            RequireLogin { _, _ ->
                                ScannerScreen(
                                    onBack = { navController.popBackStack() },
                                    onManualSearch = { navController.selectTab(BottomTab.POKEDEX) },
                                )
                            }
                        }
                        composable<CollectionCardDestination> { entry ->
                            RequireLogin { _, _ ->
                                CollectionCardDetailScreen(
                                    cardId = entry.toRoute<CollectionCardDestination>().key,
                                    onBack = { navController.popBackStack() },
                                    onIllustratorClick = { key -> navController.navigate(IllustratorDetailDestination(key)) },
                                )
                            }
                        }
                        composable<TradeRadarDestination> {
                            // Scambi fra persone vere: serve l'accesso.
                            RequireLogin { _, _ ->
                                TradeRadarScreen(
                                    onBack = { navController.popBackStack() },
                                    // Su iOS il Premium arriva con StoreKit (dopo l'account Apple a pagamento).
                                    onPremiumRequired = {},
                                )
                            }
                        }
                        composable<ComingSoonDestination> { entry ->
                            ComingSoonScreen(
                                title = entry.toRoute<ComingSoonDestination>().title,
                                message = NOT_PORTED_YET,
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                    // Lo Scanner sopra la barra, sulle quattro sezioni: come su Android.
                    if (selectedTab != null && WindowInsets.ime.getBottom(LocalDensity.current) == 0) {
                        ScannerFab(
                            onClick = { navController.navigate(ScannerDestination) },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 16.dp),
                        )
                    }
                }
                // Con la tastiera aperta la barra si toglie, come su Android: sotto la
                // tastiera non serve e ruberebbe spazio al campo su cui si scrive.
                val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
                if (selectedTab != null && !keyboardOpen) {
                    PokeVaultBottomBar(
                        selected = selectedTab,
                        onSelect = { navController.selectTab(it) },
                        // Al centro, fra Carte e Pokedex, come su Android.
                        tradeRadar = TradeRadarBarButton(pending = TradeBadge.pending) {
                            navController.navigate(TradeRadarDestination) { launchSingleTop = true }
                        },
                    )
                }
            }
        }
    }
}

/** Cambio di sezione come su Android: una pila per sezione, ritrovata tornandoci. */
private fun NavHostController.selectTab(tab: BottomTab) {
    navigate(tab.destination) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun menuTitle(routeKey: String): String = when (routeKey) {
    "graded" -> "Carte gradate"
    "competitive" -> "Competitive"
    "collector_lab" -> "Collector Lab"
    "wishlist" -> "Wishlist"
    else -> "In arrivo"
}
