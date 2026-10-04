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
import com.emabuia.pokevault.screens.auth.RequireLogin
import com.emabuia.pokevault.screens.auth.SignedInScreen
import com.emabuia.pokevault.screens.cards.ExpansionCardsScreen
import com.emabuia.pokevault.screens.expansions.ExpansionsScreen
import com.emabuia.pokevault.ui.home.HomeScreen
import com.emabuia.pokevault.ui.navigation.BottomTab
import com.emabuia.pokevault.ui.navigation.PokeVaultBottomBar
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.ui.theme.PokeVaultTheme
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

private const val COLLECTION_NEXT = "Sei dentro! La tua collezione arriva con il prossimo aggiornamento: e' la stessa dell'app Android, nello stesso account."
private const val STATS_NEXT = "Le statistiche arrivano insieme alla collezione."
private const val NOT_PORTED_YET = "Questa sezione c'e' sull'app Android e arrivera' presto anche qui."

@Composable
fun App() {
    PokeVaultTheme {
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
                                        else -> navController.navigate(ComingSoonDestination(menuTitle(routeKey)))
                                    }
                                },
                                onExpansionClick = openExpansion,
                            )
                        }
                        composable<CollectionDestination> {
                            RequireLogin { session, onLogout ->
                                SignedInScreen(
                                    title = "Le mie carte",
                                    session = session,
                                    message = COLLECTION_NEXT,
                                    onLogout = onLogout,
                                )
                            }
                        }
                        composable<PokedexDestination> {
                            ExpansionsScreen(navigateToCards = openExpansion)
                        }
                        composable<StatsDestination> {
                            RequireLogin { session, onLogout ->
                                SignedInScreen(
                                    title = "Statistiche",
                                    session = session,
                                    message = STATS_NEXT,
                                    onLogout = onLogout,
                                )
                            }
                        }
                        composable<ExpansionCardsDestination> { entry ->
                            val destination = entry.toRoute<ExpansionCardsDestination>()
                            ExpansionCardsScreen(
                                expansionId = destination.expansionId,
                                expansionName = destination.expansionName,
                                navigateBack = { navController.popBackStack() },
                            )
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
