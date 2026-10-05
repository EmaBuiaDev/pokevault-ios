package com.emabuia.pokevault.screens.illustrator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.emabuia.pokevault.ui.components.CompletionBanner
import com.emabuia.pokevault.ui.components.EmptyStateView
import com.emabuia.pokevault.ui.components.LabSearchField
import com.emabuia.pokevault.ui.components.ProgressRing
import com.emabuia.pokevault.ui.components.SkeletonBlock
import com.emabuia.pokevault.ui.components.StatTile
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale
import org.koin.compose.viewmodel.koinViewModel

/**
 * La pagina di un illustratore: IllustratorDetailScreen dell'app Android.
 * Si sfoglia come un'espansione (griglia a tre colonne, verde su cio' che si
 * possiede, velo su cio' che manca), con le carte raggruppate per set e i
 * gruppi che si chiudono. Toccando una carta si apre il suo dettaglio, da cui
 * si aggiunge.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IllustratorDetailScreen(
    illustratorKey: String,
    onBack: () -> Unit,
    onCardClick: (Card) -> Unit,
) {
    val viewModel = koinViewModel<IllustratorViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Il dettaglio parte quando l'indice c'e': arrivando da un link diretto puo' non esserci ancora.
    LaunchedEffect(illustratorKey, state.entries.size) { viewModel.loadDetail(illustratorKey) }

    val row = state.rowFor(illustratorKey)
    val displayName = row?.displayName ?: illustratorKey
    val cards = state.detailCards

    var query by remember { mutableStateOf("") }
    var showOnlyMissing by remember { mutableStateOf(false) }
    var showOnlyOwned by remember { mutableStateOf(false) }
    var collapsedSets by remember { mutableStateOf(setOf<String>()) }

    val filtered = remember(cards, query, showOnlyMissing, showOnlyOwned, state.ownedApiIds) {
        val q = query.trim().lowercase()
        cards.filter { card ->
            val owned = state.isOwned(card)
            val matchesQuery = q.isEmpty() || card.nome.lowercase().contains(q) || card.number.orEmpty().lowercase().contains(q)
            val matchesOwnership = when {
                showOnlyMissing -> !owned
                showOnlyOwned -> owned
                else -> true
            }
            matchesQuery && matchesOwnership
        }
    }
    val groups = remember(filtered, state.expansionInfo) { IllustratorViewModel.groups(filtered, state.expansionInfo) }

    Scaffold(
        containerColor = AppColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(displayName, color = AppColors.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (row != null) {
                            Text(AppLocale.illustratorCardsAndSets(row.total, row.expansionCount), color = AppColors.textMuted, fontSize = 11.sp)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppLocale.back, tint = AppColors.textPrimary)
                    }
                },
                actions = {
                    if (state.signedIn) {
                        IconButton(onClick = { viewModel.toggleFollow(illustratorKey) }) {
                            val followed = row?.isFollowed == true
                            Icon(
                                imageVector = if (followed) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = if (followed) AppLocale.illustratorUnfollow else AppLocale.illustratorFollow,
                                tint = if (followed) AppColors.gold else AppColors.textMuted,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background),
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (row != null) {
                item(span = { GridItemSpan(3) }) {
                    IllustratorHeader(
                        key = illustratorKey,
                        displayName = displayName,
                        owned = row.owned,
                        total = row.total,
                        percent = row.percent,
                        expansions = row.expansionCount,
                    )
                }
                if (row.isComplete) {
                    item(span = { GridItemSpan(3) }) {
                        CompletionBanner(title = AppLocale.illustratorCompleteTitle, subtitle = AppLocale.illustratorCompleteSubtitle(displayName))
                    }
                }
            }

            item(span = { GridItemSpan(3) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    LabSearchField(value = query, onValueChange = { query = it }, hint = AppLocale.searchCards, accent = AppColors.purple)
                    if (state.signedIn) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // I due filtri si escludono a vicenda, come nel set.
                            OwnershipChip(AppLocale.illustratorFilterMissing, showOnlyMissing) {
                                showOnlyMissing = !showOnlyMissing
                                showOnlyOwned = false
                            }
                            OwnershipChip(AppLocale.illustratorFilterOwned, showOnlyOwned) {
                                showOnlyOwned = !showOnlyOwned
                                showOnlyMissing = false
                            }
                        }
                    }
                }
            }

            // Indice arrivato ma la voce non c'e': un artista che il catalogo non conosce (piu').
            if (!state.isLoading && row == null) {
                item(span = { GridItemSpan(3) }) {
                    EmptyStateView(icon = Icons.Default.Brush, title = AppLocale.illustratorsNoMatch, subtitle = AppLocale.illustratorsEmptySubtitle)
                }
                return@LazyVerticalGrid
            }

            if ((state.isLoading || state.isDetailLoading) && cards.isEmpty()) {
                items(9) { index ->
                    SkeletonBlock(modifier = Modifier.fillMaxWidth().aspectRatio(0.72f), shape = RoundedCornerShape(10.dp), index = index)
                }
                return@LazyVerticalGrid
            }

            for (group in groups) {
                val isCollapsed = group.setId in collapsedSets
                val ownedInGroup = group.cards.count { state.isOwned(it) }

                item(span = { GridItemSpan(3) }, key = "header::${group.setId}") {
                    SetGroupHeader(
                        name = group.setName,
                        owned = ownedInGroup,
                        total = group.cards.size,
                        showProgress = state.signedIn,
                        isCollapsed = isCollapsed,
                        onToggle = { collapsedSets = if (isCollapsed) collapsedSets - group.setId else collapsedSets + group.setId },
                    )
                }

                if (!isCollapsed) {
                    items(group.cards, key = { "${group.setId}::${it.cardId}" }) { card ->
                        IllustratorCardTile(
                            card = card,
                            isOwned = state.isOwned(card),
                            dimMissing = state.signedIn,
                            onClick = { onCardClick(card) },
                        )
                    }
                }
            }
        }
    }
}

/** La tessera della griglia: bordo e spunta verdi se posseduta, velo scuro se manca. */
@Composable
private fun IllustratorCardTile(card: Card, isOwned: Boolean, dimMissing: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Column(Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(63f / 88f)
                .clip(shape)
                .then(if (isOwned) Modifier.border(2.dp, AppColors.green, shape) else Modifier),
        ) {
            AsyncImage(
                model = card.imageUrl(WORKER_BASE_URL),
                contentDescription = card.nome,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            if (dimMissing && !isOwned) {
                Box(Modifier.fillMaxSize().background(AppColors.background.copy(alpha = 0.45f)))
            }
            if (isOwned) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp).clip(CircleShape).background(AppColors.green),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = AppColors.onAccent, modifier = Modifier.size(14.dp))
                }
            }
        }
        Text(card.nome, color = AppColors.textPrimary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
        Text("#${card.number.orEmpty()}", color = AppColors.textMuted, fontSize = 10.sp)
    }
}

@Composable
private fun IllustratorHeader(key: String, displayName: String, owned: Int, total: Int, percent: Float, expansions: Int) {
    // `card` sopra `background` col filo di bordo: nel tema chiaro surface e card sono lo stesso bianco.
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.card, RoundedCornerShape(18.dp))
            .border(1.dp, AppColors.textMuted.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IllustratorAvatar(key = key, displayName = displayName, size = 52.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(displayName, color = AppColors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("$owned/$total", color = AppColors.textSecondary, fontSize = 13.sp)
            }
            ProgressRing(percent = percent, accent = AppColors.purple, size = 54.dp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(label = AppLocale.chaseStatOwned, value = owned.toString(), icon = Icons.Default.Style, accent = AppColors.green, modifier = Modifier.weight(1f))
            StatTile(label = AppLocale.chaseStatMissing, value = (total - owned).coerceAtLeast(0).toString(), icon = Icons.Default.Style, accent = AppColors.red, modifier = Modifier.weight(1f))
            StatTile(label = AppLocale.illustratorsSets(expansions), value = expansions.toString(), icon = Icons.Default.PhotoLibrary, accent = AppColors.purple, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun SetGroupHeader(name: String, owned: Int, total: Int, showProgress: Boolean, isCollapsed: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 10.dp),
    ) {
        Text(name, color = AppColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(
            if (showProgress) "$owned/$total" else "$total",
            color = if (showProgress && owned >= total) AppColors.green else AppColors.textMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Icon(
            imageVector = if (isCollapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
            contentDescription = null,
            tint = AppColors.textMuted,
            modifier = Modifier.padding(start = 6.dp).size(18.dp),
        )
    }
}

@Composable
private fun OwnershipChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val accent = AppColors.purple
    Box(
        modifier = Modifier
            .background(if (selected) accent.copy(alpha = 0.18f) else AppColors.card, RoundedCornerShape(10.dp))
            .border(1.dp, if (selected) accent.copy(alpha = 0.55f) else AppColors.textMuted.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, color = if (selected) accent else AppColors.textSecondary, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}
