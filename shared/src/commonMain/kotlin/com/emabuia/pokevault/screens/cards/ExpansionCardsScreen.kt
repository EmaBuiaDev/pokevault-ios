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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.emabuia.pokevault.data.Card
import com.emabuia.pokevault.data.ExpansionCards
import com.emabuia.pokevault.data.PriceEntry
import com.emabuia.pokevault.data.WORKER_BASE_URL
import com.emabuia.pokevault.resources.Res
import com.emabuia.pokevault.resources.back
import com.emabuia.pokevault.resources.card_illustrator
import com.emabuia.pokevault.resources.card_open_cardmarket
import com.emabuia.pokevault.resources.cards_error
import com.emabuia.pokevault.resources.close
import com.emabuia.pokevault.resources.retry
import com.emabuia.pokevault.ui.components.RarityMarkWithLabel
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
                is CardsState.Ready -> CardGrid(current.content)
            }
        }
    }
}

@Composable
private fun CardGrid(content: ExpansionCards) {
    var selected by remember { mutableStateOf<Card?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(110.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
    ) {
        items(content.cards, key = { it.cardId }) { card ->
            CardCell(card, content.priceOf(card), onClick = { selected = card })
        }
    }

    selected?.let { card ->
        CardDetailDialog(card, content.priceOf(card), onDismiss = { selected = null })
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

@Composable
private fun CardDetailDialog(card: Card, price: PriceEntry?, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp)) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AsyncImage(
                    model = card.imageUrl(WORKER_BASE_URL, size = "high"),
                    contentDescription = card.nome,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().aspectRatio(CARD_RATIO).clip(RoundedCornerShape(10.dp)),
                )
                Text(card.nome, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("#${card.number.orEmpty()}", style = MaterialTheme.typography.bodyMedium)
                    val rarity = RarityUtils.getRarityInfo(card.rarity)
                    if (!rarity.isUnknown) RarityMarkWithLabel(rarity, fontSize = 13)
                    price?.displayText()?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = AppColors.green)
                    }
                }
                card.illustratore?.let {
                    Text(stringResource(Res.string.card_illustrator, it), style = MaterialTheme.typography.bodySmall)
                }
                Row(Modifier.padding(top = 8.dp)) {
                    price?.url?.let { url ->
                        TextButton(onClick = { uriHandler.openUri(url) }) {
                            Text(stringResource(Res.string.card_open_cardmarket))
                        }
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(Res.string.close)) }
                }
            }
        }
    }
}
