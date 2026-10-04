package com.emabuia.pokevault

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.emabuia.pokevault.screens.cards.ExpansionCardsScreen
import com.emabuia.pokevault.screens.expansions.ExpansionsScreen
import kotlinx.serialization.Serializable

@Serializable
object ExpansionsDestination

@Serializable
data class ExpansionCardsDestination(val expansionId: String, val expansionName: String)

@Composable
fun App() {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    ) {
        Surface {
            val navController: NavHostController = rememberNavController()
            NavHost(navController = navController, startDestination = ExpansionsDestination) {
                composable<ExpansionsDestination> {
                    ExpansionsScreen(navigateToCards = { expansion ->
                        navController.navigate(ExpansionCardsDestination(expansion.id, expansion.name))
                    })
                }
                composable<ExpansionCardsDestination> { backStackEntry ->
                    val destination = backStackEntry.toRoute<ExpansionCardsDestination>()
                    ExpansionCardsScreen(
                        expansionId = destination.expansionId,
                        expansionName = destination.expansionName,
                        navigateBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
