package com.emabuia.pokevault.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.emabuia.pokevault.data.Expansion
import com.emabuia.pokevault.data.ExpansionsState
import com.emabuia.pokevault.data.WORKER_BASE_URL
import com.emabuia.pokevault.screens.auth.AuthViewModel
import com.emabuia.pokevault.screens.expansions.ExpansionsViewModel
import com.emabuia.pokevault.ui.components.pressScale
import com.emabuia.pokevault.ui.home.components.MenuGrid
import com.emabuia.pokevault.ui.home.components.HomeSearchEntry
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale
import org.koin.compose.viewmodel.koinViewModel

/**
 * La Home: saluto, la griglia delle sezioni (MenuGrid, la stessa dell'app
 * Android) e le ultime espansioni uscite.
 *
 * Su Android sotto la griglia ci sono le ultime carte aggiunte alla collezione:
 * qui la collezione non c'e' ancora (arriva col login), e al loro posto stanno
 * le ultime uscite, che non hanno bisogno di un account.
 */
@Composable
fun HomeScreen(
    onMenuClick: (routeKey: String) -> Unit,
    onExpansionClick: (Expansion) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val viewModel = koinViewModel<ExpansionsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session by koinViewModel<AuthViewModel>().session.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(16.dp))
        Row(Modifier.padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
            Text("Ciao, ${session?.name ?: "Allenatore"}!", color = AppColors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Gestisci la tua collezione con stile ✨", color = AppColors.textSecondary, fontSize = 14.sp)
            }
            // Come su Android: le impostazioni dall'ingranaggio accanto al saluto.
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, contentDescription = AppLocale.settingsTitle, tint = AppColors.textSecondary)
            }
        }
        Spacer(Modifier.height(16.dp))
        // Come su Android: la ricerca su tutto il catalogo e' il primo gesto, e
        // qui e' un pulsante che porta al Pokedex, dove c'e' il campo vero.
        HomeSearchEntry(onClick = onSearchClick)
        Spacer(Modifier.height(16.dp))

        MenuGrid(onItemClick = onMenuClick)

        Spacer(Modifier.height(24.dp))
        Text(
            "Ultime uscite",
            color = AppColors.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(12.dp))
        (state as? ExpansionsState.Ready)?.let { ready ->
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(ready.expansions.take(8), key = { it.id }) { expansion ->
                    LatestExpansionTile(expansion, onClick = { onExpansionClick(expansion) })
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LatestExpansionTile(expansion: Expansion, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .pressScale(onClick = onClick)
            .width(140.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.card)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = expansion.logoUrl(WORKER_BASE_URL),
            contentDescription = expansion.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            expansion.name,
            color = AppColors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            expansion.releaseDate.orEmpty(),
            color = AppColors.textMuted,
            fontSize = 11.sp,
        )
    }
}
