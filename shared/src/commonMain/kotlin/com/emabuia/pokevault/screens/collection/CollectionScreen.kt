package com.emabuia.pokevault.screens.collection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.emabuia.pokevault.data.CollectionStats
import com.emabuia.pokevault.data.Session
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.ui.components.CardImageSkeleton
import com.emabuia.pokevault.ui.components.OwnedVariantBadges
import com.emabuia.pokevault.ui.components.RarityMarkWithLabel
import com.emabuia.pokevault.ui.components.formatEur
import com.emabuia.pokevault.ui.components.holoFoil
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.CardGroup
import com.emabuia.pokevault.util.ImageUrlUtils
import com.emabuia.pokevault.util.RarityUtils
import org.koin.compose.viewmodel.koinViewModel

private const val COLUMNS = 3

/**
 * La collezione, in sola lettura: totali, carte divise per espansione, e il
 * dettaglio di una carta al tocco. Aggiungere e togliere carte arriva dopo.
 */
@Composable
fun CollectionScreen(session: Session, onLogout: () -> Unit) {
    val viewModel = koinViewModel<CollectionViewModel>(key = session.uid)
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<CardGroup?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Le mie carte", color = AppColors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(session.name, color = AppColors.textMuted, fontSize = 12.sp)
            }
            if (state.isLoading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            TextButton(onClick = onLogout) { Text("Esci", color = AppColors.red) }
        }

        SummaryStrip(state.stats)

        state.errorMessage?.let {
            Text(it, color = AppColors.red, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        }

        if (!state.isLoading && state.sections.isEmpty() && state.errorMessage == null) {
            Text(
                "La collezione e' vuota: le carte che aggiungi dall'app Android compaiono qui.",
                color = AppColors.textSecondary,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(32.dp),
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(COLUMNS),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.sections.forEach { section ->
                item(key = "header-${section.expansion}", span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(
                            section.label,
                            color = AppColors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${section.totalQuantity} carte · ${formatEur(section.totalValue)}",
                            color = AppColors.textMuted,
                            fontSize = 12.sp,
                        )
                    }
                }
                items(section.groups, key = { it.key }) { group ->
                    CollectionCardGridItem(
                        card = group.representative,
                        gridColumns = COLUMNS,
                        ownedVariants = group.variants,
                        onClick = { selected = group },
                    )
                }
            }
        }
    }

    selected?.let { group -> CardGroupDialog(group, onDismiss = { selected = null }) }
}

/** SummaryStrip dell'app Android, senza la riga dei filtri (qui non ci sono ancora). */
@Composable
private fun SummaryStrip(stats: CollectionStats) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(AppColors.blue.copy(alpha = 0.16f), AppColors.surface)))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Valore", color = AppColors.textMuted, fontSize = 11.sp)
                Text(formatEur(stats.totalValue), color = AppColors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            SummaryNumber(value = stats.totalCards, label = "Carte", color = AppColors.blue)
            Spacer(modifier = Modifier.width(18.dp))
            SummaryNumber(value = stats.uniqueCards, label = "Uniche", color = AppColors.purple)
        }
    }
}

@Composable
private fun SummaryNumber(value: Int, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.End) {
        Text("$value", color = color, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = AppColors.textMuted, fontSize = 11.sp)
    }
}

/**
 * CollectionCardGridItem dell'app Android, senza selezione multipla e senza
 * la transizione condivisa verso il dettaglio (arrivano con quelle funzioni).
 */
@Composable
fun CollectionCardGridItem(
    card: PokemonCard,
    gridColumns: Int = 3,
    ownedVariants: Set<String> = emptySet(),
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val compact = gridColumns > 4
    val corner = if (compact) 4.dp else 10.dp
    val imageUrl = remember(card.imageUrl) { ImageUrlUtils.safeProxiedImageUrl(card.imageUrl) }
    var failed by remember(imageUrl) { mutableStateOf(imageUrl.isBlank()) }

    Box(
        modifier = modifier
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(corner))
            .background(AppColors.card)
            .border(
                width = if (compact) 0.5.dp else 1.dp,
                color = AppColors.textMuted.copy(alpha = 0.15f),
                shape = RoundedCornerShape(corner),
            )
            .clickable(onClick = onClick)
    ) {
        if (failed) {
            // Il nome della carta al posto dell'icona "immagine rotta":
            // una tessera senza immagine deve dire che carta e'.
            CollectionCardImageFallback(card = card, compact = compact)
        } else {
            // Sotto l'immagine, non dentro: resta visibile mentre arriva.
            CardImageSkeleton(number = card.cardNumber)
            AsyncImage(
                model = imageUrl,
                contentDescription = card.name,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier
                    .fillMaxSize()
                    .holoFoil(enabled = RarityUtils.hasFoilFinish(card.rarity)),
            )
        }

        // Le stampe possedute in alto a sinistra: dall'altra parte c'e' il contatore.
        if (ownedVariants.isNotEmpty() && gridColumns <= 4) {
            OwnedVariantBadges(
                variants = ownedVariants,
                size = if (gridColumns > 3) 13 else 16,
                fontSize = if (gridColumns > 3) 7 else 9,
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(if (compact) 2.dp else 4.dp)
                .size(if (compact) 14.dp else 22.dp)
                .clip(CircleShape)
                .background(AppColors.blue),
            contentAlignment = Alignment.Center,
        ) {
            val quantityFontSize = if (compact) 7.sp else 10.sp
            Text(
                text = "x${card.quantity}",
                color = Color.White,
                fontSize = quantityFontSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                style = TextStyle(lineHeight = quantityFontSize),
            )
        }
    }
}

@Composable
private fun CollectionCardImageFallback(card: PokemonCard, compact: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.surface)
            .padding(if (compact) 4.dp else 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = card.name,
                color = AppColors.textPrimary,
                fontSize = if (compact) 8.sp else 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            if (card.cardNumber.isNotBlank()) {
                Text(text = "#${card.cardNumber}", color = AppColors.textMuted, fontSize = if (compact) 7.sp else 8.sp, maxLines = 1)
            }
        }
    }
}

/** Una carta con le sue stampe: quante copie, di che lingua, quanto valgono. */
@Composable
private fun CardGroupDialog(group: CardGroup, onDismiss: () -> Unit) {
    val card = group.representative
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = AppColors.surface) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AsyncImage(
                    model = ImageUrlUtils.safeProxiedImageUrl(card.imageUrl),
                    contentDescription = card.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.72f).clip(RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.height(12.dp))
                Text(card.name, color = AppColors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    listOf(group.expansionLabel, card.cardNumber.takeIf { it.isNotBlank() }?.let { "#$it" })
                        .filterNotNull().joinToString(" · "),
                    color = AppColors.textSecondary,
                    fontSize = 13.sp,
                )
                val rarity = RarityUtils.getRarityInfo(card.rarity)
                if (!rarity.isUnknown) RarityMarkWithLabel(rarity, fontSize = 13, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(12.dp))
                group.cards.forEach { print ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(
                            listOf(print.variant, print.language, print.condition).filter { it.isNotBlank() }.joinToString(" · "),
                            color = AppColors.textPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text("x${print.quantity}", color = AppColors.blue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        if (print.estimatedValue > 0) {
                            Text("  ${formatEur(print.estimatedValue)}", color = AppColors.green, fontSize = 13.sp)
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 8.dp)) { Text("Chiudi") }
            }
        }
    }
}
