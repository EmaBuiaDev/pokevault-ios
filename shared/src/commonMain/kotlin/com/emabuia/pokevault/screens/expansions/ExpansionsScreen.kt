package com.emabuia.pokevault.screens.expansions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.data.Card
import com.emabuia.pokevault.ui.home.components.SearchBar
import com.emabuia.pokevault.ui.theme.AppColors
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.emabuia.pokevault.data.Expansion
import com.emabuia.pokevault.data.ExpansionsState
import com.emabuia.pokevault.data.WORKER_BASE_URL
import com.emabuia.pokevault.resources.Res
import com.emabuia.pokevault.resources.expansions_cards
import com.emabuia.pokevault.resources.expansions_error
import com.emabuia.pokevault.resources.expansions_title
import com.emabuia.pokevault.resources.retry
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ExpansionsScreen(navigateToCards: (Expansion) -> Unit, onCardClick: (Card) -> Unit) {
    val viewModel = koinViewModel<ExpansionsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val searchViewModel = koinViewModel<SearchViewModel>()
    val search by searchViewModel.state.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .background(AppColors.background)
            // Sotto c'e' la bottom bar, che tiene conto da se' del bordo dello schermo.
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
    ) {
        Text(
            stringResource(Res.string.expansions_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(16.dp),
        )
        SearchBar(query = search.query, onQueryChange = searchViewModel::onQueryChange)
        Spacer(Modifier.height(8.dp))

        if (search.isActive) {
            val names = (state as? ExpansionsState.Ready)?.expansions?.associate { it.id to it.name }.orEmpty()
            SearchResults(search, names, onCardClick)
            return@Column
        }

        when (val current = state) {
            ExpansionsState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is ExpansionsState.Error -> Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(Res.string.expansions_error), style = MaterialTheme.typography.titleMedium)
                Text(current.message, style = MaterialTheme.typography.bodySmall)
                Button(onClick = viewModel::retry, modifier = Modifier.padding(top = 16.dp)) {
                    Text(stringResource(Res.string.retry))
                }
            }
            is ExpansionsState.Ready -> ExpansionList(current.expansions, navigateToCards)
        }
    }
}

/** Le carte trovate in tutto il catalogo, con il set da cui vengono. */
@Composable
private fun SearchResults(search: SearchState, expansionNames: Map<String, String>, onCardClick: (Card) -> Unit) {
    when {
        search.errorMessage != null -> Text(search.errorMessage, color = AppColors.red, modifier = Modifier.padding(20.dp))
        search.isSearching && search.results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        search.results.isEmpty() -> Text(
            "Nessuna carta trovata",
            color = AppColors.textSecondary,
            modifier = Modifier.padding(20.dp),
        )
        else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(110.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            gridItems(search.results, key = { it.cardId }) { card ->
                Column(Modifier.clickable { onCardClick(card) }) {
                    AsyncImage(
                        model = card.imageUrl(WORKER_BASE_URL),
                        contentDescription = card.nome,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().aspectRatio(63f / 88f).clip(RoundedCornerShape(6.dp)),
                    )
                    Text(
                        card.nome,
                        color = AppColors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        "${expansionNames[card.espansioneId] ?: card.espansioneId.uppercase()} · #${card.number.orEmpty()}",
                        color = AppColors.textMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpansionList(expansions: List<Expansion>, onClick: (Expansion) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(expansions, key = { it.id }) { expansion ->
            ExpansionRow(expansion, onClick = { onClick(expansion) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun ExpansionRow(expansion: Expansion, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = expansion.logoUrl(WORKER_BASE_URL),
            contentDescription = expansion.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(width = 96.dp, height = 48.dp),
        )
        Column(Modifier.padding(start = 16.dp)) {
            Text(expansion.name, style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(
                    expansion.series,
                    stringResource(Res.string.expansions_cards, expansion.cardCount),
                    expansion.releaseDate,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
