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
import com.emabuia.pokevault.ui.home.components.RecentCardsSection
import com.emabuia.pokevault.ui.home.components.WelcomeHeader
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale
import org.koin.compose.viewmodel.koinViewModel

/**
 * La Home dell'app Android: saluto con lo sprite, ricerca, la griglia delle
 * sezioni e le ultime carte aggiunte alla collezione.
 *
 * Su iOS la Home si apre anche senza accesso (il catalogo non chiede un
 * account): in quel caso al posto delle carte recenti stanno le ultime
 * espansioni uscite. Manca il banner "sei offline", che su iOS non ha ancora
 * un modo di sapere se c'e' rete.
 */
@Composable
fun HomeScreen(
    onMenuClick: (routeKey: String) -> Unit,
    onExpansionClick: (Expansion) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onRecentCardClick: (key: String) -> Unit = {},
    onSeeAllRecent: () -> Unit = {},
    onScan: () -> Unit = {},
) {
    val viewModel = koinViewModel<ExpansionsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val session by koinViewModel<AuthViewModel>().session.collectAsStateWithLifecycle()
    val homeViewModel = koinViewModel<HomeViewModel>()
    val selectedHomeSpriteId by homeViewModel.selectedHomeSpriteId.collectAsStateWithLifecycle()

    // La cascata parte al primo frame utile e il flag resta acceso nel
    // ViewModel: tornando sulla Home da un'altra tab la griglia e' gia' li'.
    LaunchedEffect(Unit) { homeViewModel.markEntered() }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .verticalScroll(scrollState)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Header: sprite + nome + impostazioni su una riga
        WelcomeHeader(
            userName = session?.name ?: "Allenatore",
            selectedPokemonId = if (homeViewModel.isPremium && selectedHomeSpriteId != 0) selectedHomeSpriteId else null,
            onSettingsClick = onSettingsClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                // Parallax: l'header sfuma e si stacca in su un po' piu' in
                // fretta del contenuto, tutto in fase di disegno.
                .graphicsLayer {
                    val offset = scrollState.value.toFloat()
                    alpha = (1f - offset / 110.dp.toPx()).coerceAtLeast(0f)
                    translationY = -(offset * 0.35f).coerceAtMost(20.dp.toPx())
                }
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Come su Android: la ricerca su tutto il catalogo e' il primo gesto, e
        // qui e' un pulsante che porta al Pokedex, dove c'e' il campo vero.
        HomeSearchEntry(onClick = onSearchClick)

        Spacer(modifier = Modifier.height(16.dp))

        MenuGrid(cascadeVisible = homeViewModel.hasEnteredOnce, onItemClick = onMenuClick)

        if (session != null) {
            // Le ultime carte aggiunte: il tocco apre la carta con la chiave di
            // gruppo, la stessa che usa Collezione.
            RecentCardsSection(
                groups = homeViewModel.recentGroups,
                hasCards = homeViewModel.hasCards,
                isLoading = homeViewModel.isLoading,
                onCardClick = onRecentCardClick,
                onSeeAll = onSeeAllRecent,
                onScan = onScan
            )
        } else {
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
        }

        // Spazio per il pulsante dello scanner, che galleggia sopra questa colonna.
        Spacer(modifier = Modifier.height(80.dp))
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
