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
import com.emabuia.pokevault.screens.cards.ExpansionCardsScreen
import com.emabuia.pokevault.screens.collection.CollectionScreen
import com.emabuia.pokevault.screens.expansions.ExpansionsScreen
import com.emabuia.pokevault.screens.graded.GradedCardsScreen
import com.emabuia.pokevault.screens.illustrator.IllustratorDetailScreen
import com.emabuia.pokevault.screens.illustrator.IllustratorListScreen
import com.emabuia.pokevault.screens.settings.SettingsScreen
import com.emabuia.pokevault.screens.stats.StatsScreen
import com.emabuia.pokevault.screens.wishlist.WishlistScreen
import com.emabuia.pokevault.ui.home.HomeScreen
import com.emabuia.pokevault.ui.navigation.BottomTab
import com.emabuia.pokevault.ui.navigation.PokeVaultBottomBar
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.ui.theme.PokeVaultTheme
import com.emabuia.pokevault.ui.theme.ThemeMode
import com.emabuia.pokevault.ui.theme.ThemePreference
import org.koin.compose.koinInject
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import kotlinx.serialization.Serializable

@Serializable
object HomeDestination

@Serializable
object CollectionDestination

@Serializable
object PokedexDestination

@Serializable
object StatsDestination

@Serializable
data class ExpansionCardsDestination(val expansionId: String, val expansionName: String)

@Serializable
data class CardDetailDestination(val expansionId: String, val cardId: String)

@Serializable
object WishlistDestination

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
                navController.navigate(ExpansionCardsDestination(expansion.id, expansion.name))
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
                                )
                            }
                        }
                        composable<PokedexDestination> {
                            ExpansionsScreen(
                                navigateToCards = openExpansion,
                                onCardClick = { card -> navController.navigate(CardDetailDestination(card.espansioneId, card.cardId)) },
                            )
                        }
                        composable<StatsDestination> {
                            RequireLogin { _, _ -> StatsScreen() }
                        }
                        composable<ExpansionCardsDestination> { entry ->
                            val destination = entry.toRoute<ExpansionCardsDestination>()
                            ExpansionCardsScreen(
                                expansionId = destination.expansionId,
                                expansionName = destination.expansionName,
                                navigateBack = { navController.popBackStack() },
                                onCardClick = { card -> navController.navigate(CardDetailDestination(card.espansioneId, card.cardId)) },
                            )
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
                                WishlistScreen(
                                    onBack = { navController.popBackStack() },
                                    onCardClick = { card -> navController.navigate(CardDetailDestination(card.espansioneId, card.cardId)) },
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
                }
                // Con la tastiera aperta la barra si toglie, come su Android: sotto la
                // tastiera non serve e ruberebbe spazio al campo su cui si scrive.
                val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
                if (selectedTab != null && !keyboardOpen) {
                    PokeVaultBottomBar(selected = selectedTab, onSelect = { navController.selectTab(it) })
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
    "trade_radar" -> "TradeRadar"
    else -> "In arrivo"
}
