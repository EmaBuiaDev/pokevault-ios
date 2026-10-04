package com.emabuia.pokevault.screens.cards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.emabuia.pokevault.data.Card
import com.emabuia.pokevault.data.ExpansionCards
import com.emabuia.pokevault.data.PriceEntry
import com.emabuia.pokevault.data.WORKER_BASE_URL
import com.emabuia.pokevault.resources.Res
import com.emabuia.pokevault.resources.back
import com.emabuia.pokevault.resources.cards_error
import com.emabuia.pokevault.resources.retry
import com.emabuia.pokevault.ui.components.RarityOverlayBadge
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.RarityUtils
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

// Proporzioni di una carta Pokemon: 63 x 88 mm.
private const val CARD_RATIO = 63f / 88f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpansionCardsScreen(
    expansionId: String,
    expansionName: String,
    navigateBack: () -> Unit,
    onCardClick: (Card) -> Unit,
) {
    val viewModel = koinViewModel<ExpansionCardsViewModel>(key = expansionId) { parametersOf(expansionId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = AppColors.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background),
                title = { Text(expansionName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = navigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                CardsState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is CardsState.Error -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(Res.string.cards_error), style = MaterialTheme.typography.titleMedium)
                    Text(current.message, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = viewModel::retry, modifier = Modifier.padding(top = 16.dp)) {
                        Text(stringResource(Res.string.retry))
                    }
                }
                is CardsState.Ready -> CardGrid(current.content, onCardClick)
            }
        }
    }
}

@Composable
private fun CardGrid(content: ExpansionCards, onCardClick: (Card) -> Unit) {

    LazyVerticalGrid(
        columns = GridCells.Adaptive(110.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
    ) {
        items(content.cards, key = { it.cardId }) { card ->
            CardCell(card, content.priceOf(card), onClick = { onCardClick(card) })
        }
    }
}

@Composable
private fun CardCell(card: Card, price: PriceEntry?, onClick: () -> Unit) {
    val rarity = RarityUtils.getRarityInfo(card.rarity)
    Column(Modifier.padding(6.dp).clickable(onClick = onClick)) {
        Box {
            AsyncImage(
                model = card.imageUrl(WORKER_BASE_URL),
                contentDescription = card.nome,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().aspectRatio(CARD_RATIO).clip(RoundedCornerShape(6.dp)),
            )
            if (!rarity.isUnknown) {
                RarityOverlayBadge(rarity, Modifier.align(Alignment.TopEnd).padding(4.dp))
            }
        }
        Text(
            card.nome,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("#${card.number.orEmpty()}", style = MaterialTheme.typography.bodySmall)
            price?.displayText()?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = AppColors.green)
            }
        }
    }
}

