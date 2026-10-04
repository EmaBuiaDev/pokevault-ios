package com.emabuia.pokevault.screens.wishlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.emabuia.pokevault.data.Card
import com.emabuia.pokevault.data.WORKER_BASE_URL
import com.emabuia.pokevault.data.WishlistContent
import com.emabuia.pokevault.ui.components.formatEur
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.ui.wishlist.WishlistBadge
import com.emabuia.pokevault.ui.wishlist.wishlistAccentColor
import com.emabuia.pokevault.util.AppLocale
import org.koin.compose.viewmodel.koinViewModel

/**
 * Le wishlist dell'account, in sola lettura: l'elenco, e toccando una lista
 * le sue carte col prezzo minimo. Crearle e modificarle resta sull'app Android
 * finche' le scritture non hanno i loro test.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreen(onBack: () -> Unit, onCardClick: (Card) -> Unit) {
    val viewModel = koinViewModel<WishlistViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val open = state.lists.firstOrNull { it.wishlist.id == openId }

    Scaffold(
        containerColor = AppColors.background,
        topBar = {
            TopAppBar(
                title = { Text(open?.wishlist?.name ?: AppLocale.wishlistTitle, color = AppColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { if (open != null) openId = null else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, AppLocale.back, tint = AppColors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.errorMessage != null -> Text(state.errorMessage!!, color = AppColors.red, modifier = Modifier.padding(20.dp))
                state.lists.isEmpty() -> Text(
                    "Non hai ancora wishlist: le crei dall'app Android e compaiono qui.",
                    color = AppColors.textSecondary,
                    modifier = Modifier.padding(24.dp),
                )
                open != null -> WishlistCards(open, onCardClick)
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.lists, key = { it.wishlist.id }) { content ->
                        WishlistRow(content, onClick = { openId = content.wishlist.id })
                    }
                }
            }
        }
    }
}

@Composable
private fun WishlistRow(content: WishlistContent, onClick: () -> Unit) {
    val list = content.wishlist
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.card)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WishlistBadge(iconKey = list.iconKey, accentKey = list.resolvedAccentKey)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(list.name, color = AppColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("${list.cardIds.size} carte", color = AppColors.textMuted, fontSize = 12.sp)
            if (list.budgetEur > 0) {
                BudgetBar(spent = content.totalValue, budget = list.budgetEur, accent = wishlistAccentColor(list.resolvedAccentKey))
            }
        }
        Text(formatEur(content.totalValue), color = AppColors.green, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** Il totale della lista contro il budget che l'utente si e' dato. */
@Composable
private fun BudgetBar(spent: Double, budget: Double, accent: androidx.compose.ui.graphics.Color) {
    val fraction = (spent / budget).toFloat().coerceIn(0f, 1f)
    Column(Modifier.padding(top = 6.dp)) {
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(AppColors.textMuted.copy(alpha = 0.2f))) {
            Box(Modifier.fillMaxWidth(fraction).height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (spent > budget) AppColors.red else accent))
        }
        Text("Budget ${formatEur(budget)}", color = AppColors.textMuted, fontSize = 11.sp)
    }
}

@Composable
private fun WishlistCards(content: WishlistContent, onCardClick: (Card) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(110.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text(
                    "${content.cards.size} carte · ${formatEur(content.totalValue)}",
                    color = AppColors.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (content.unresolved > 0) {
                    Text(
                        "${content.unresolved} carte di questa lista non si possono ancora mostrare su iOS: sull'app Android ci sono.",
                        color = AppColors.textMuted,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        items(content.cards.size) { index ->
            val item = content.cards[index]
            Column(Modifier.clickable { onCardClick(item.card) }) {
                AsyncImage(
                    model = item.card.imageUrl(WORKER_BASE_URL),
                    contentDescription = item.card.nome,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().aspectRatio(63f / 88f).clip(RoundedCornerShape(6.dp)),
                )
                Text(item.card.nome, color = AppColors.textPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                item.price?.displayText()?.let { Text(it, color = AppColors.green, fontSize = 12.sp) }
            }
        }
    }
}
