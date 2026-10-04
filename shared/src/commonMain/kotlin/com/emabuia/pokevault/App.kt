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
import com.emabuia.pokevault.screens.expansions.ExpansionsScreen
import kotlinx.serialization.Serializable

@Serializable
object ExpansionsDestination

@Composable
fun App() {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    ) {
        Surface {
            val navController: NavHostController = rememberNavController()
            NavHost(navController = navController, startDestination = ExpansionsDestination) {
                composable<ExpansionsDestination> {
                    ExpansionsScreen()
                }
            }
        }
    }
}
