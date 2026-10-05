package com.emabuia.pokevault.screens.illustrator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emabuia.pokevault.ui.components.CascadeIn
import com.emabuia.pokevault.ui.components.EmptyStateView
import com.emabuia.pokevault.ui.components.FillBar
import com.emabuia.pokevault.ui.components.LabSearchField
import com.emabuia.pokevault.ui.components.SectionHeader
import com.emabuia.pokevault.ui.components.SkeletonBlock
import com.emabuia.pokevault.ui.components.SortChipRow
import com.emabuia.pokevault.ui.components.pressScale
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.IllustratorRow
import com.emabuia.pokevault.util.IllustratorSort
import com.emabuia.pokevault.util.Illustrators
import org.koin.compose.viewmodel.koinViewModel

/**
 * L'elenco degli illustratori del catalogo, ognuno col suo avanzamento:
 * IllustratorListScreen dell'app Android. La stellina tiene in cima quelli
 * che interessano; si vede solo con l'accesso, perche' si salva sull'account.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IllustratorListScreen(
    onBack: () -> Unit,
    onIllustratorClick: (String) -> Unit,
) {
    val viewModel = koinViewModel<IllustratorViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var sortIndex by remember { mutableIntStateOf(0) }
    val sort = when (sortIndex) {
        1 -> IllustratorSort.CARDS
        2 -> IllustratorSort.NAME
        else -> IllustratorSort.CLOSEST
    }

    val rows = state.rows
    val visible = remember(rows, query, sort) {
        Illustrators.sort(Illustrators.filter(rows, query), sort)
    }
    // I seguiti si staccano solo quando non si sta cercando: durante una
    // ricerca l'utente vuole il risultato, non la sua categoria.
    val isSearching = query.isNotBlank()
    val (followed, others) = remember(visible, isSearching) {
        if (isSearching) emptyList<IllustratorRow>() to visible
        else Illustrators.partitionFollowed(visible)
    }

    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(state.isLoading) { if (!state.isLoading) revealed = true }

    Scaffold(
        containerColor = AppColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(AppLocale.illustratorsTitle, color = AppColors.textPrimary, fontWeight = FontWeight.Bold)
                        Text(
                            if (rows.isEmpty()) AppLocale.illustratorsSubtitle else AppLocale.illustratorsCount(rows.size),
                            color = AppColors.textMuted,
                            fontSize = 11.sp,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppLocale.back, tint = AppColors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background),
            )
        },
    ) { padding ->
        if (state.isLoading && rows.isEmpty()) {
            IllustratorListSkeleton(modifier = Modifier.padding(padding))
            return@Scaffold
        }

        if (rows.isEmpty()) {
            Column(Modifier.padding(padding).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                EmptyStateView(
                    icon = Icons.Default.Brush,
                    title = AppLocale.illustratorsEmptyTitle,
                    subtitle = AppLocale.illustratorsEmptySubtitle,
                )
                TextButton(onClick = viewModel::loadIndex) { Text(AppLocale.retry, color = AppColors.purple) }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp),
        ) {
            item(key = "search") {
                CascadeIn(index = 0, visible = revealed) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LabSearchField(
                            value = query,
                            onValueChange = { query = it },
                            hint = AppLocale.illustratorSearchHint,
                            accent = AppColors.purple,
                        )
                        SortChipRow(
                            labels = listOf(
                                AppLocale.illustratorSortClosest,
                                AppLocale.illustratorSortCards,
                                AppLocale.illustratorSortName,
                            ),
                            selectedIndex = sortIndex,
                            onSelect = { sortIndex = it },
                            accent = AppColors.purple,
                        )
                    }
                }
            }

            if (followed.isNotEmpty()) {
                item(key = "followed-header") { SectionHeader(title = AppLocale.illustratorsFollowed) }
                items(followed, key = { "f::${it.key}" }) { row ->
                    IllustratorRowCard(
                        row = row,
                        canFollow = state.signedIn,
                        onClick = { onIllustratorClick(row.key) },
                        onToggleFollow = { viewModel.toggleFollow(row.key) },
                        modifier = Modifier.animateItem(),
                    )
                }
                item(key = "all-header") { SectionHeader(title = AppLocale.illustratorsAll) }
            }

            if (others.isEmpty() && isSearching) {
                item(key = "no-match") {
                    Text(
                        AppLocale.illustratorsNoMatch,
                        color = AppColors.textMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }

            items(others, key = { it.key }) { row ->
                IllustratorRowCard(
                    row = row,
                    canFollow = state.signedIn,
                    onClick = { onIllustratorClick(row.key) },
                    onToggleFollow = { viewModel.toggleFollow(row.key) },
                    modifier = Modifier.animateItem(),
                )
            }

            // Le due righe oneste: senza, la somma dei totali non torna col catalogo.
            item(key = "gaps") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 18.dp)) {
                    if (state.cardsWithoutIllustrator > 0) {
                        Text(AppLocale.illustratorsCatalogGap(state.cardsWithoutIllustrator), color = AppColors.textMuted, fontSize = 11.sp)
                    }
                    if (state.nonItalianOwnedCount > 0) {
                        Text(AppLocale.illustratorsCollectionGap(state.nonItalianOwnedCount), color = AppColors.textMuted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun IllustratorRowCard(
    row: IllustratorRow,
    canFollow: Boolean,
    onClick: () -> Unit,
    onToggleFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // `card` su `background` col filo di bordo, mai `surface`: nel tema chiaro
    // surface e card sono lo stesso bianco, e una tessera su surface sparirebbe.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(AppColors.card, RoundedCornerShape(16.dp))
            .border(1.dp, AppColors.textMuted.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .pressScale(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        IllustratorAvatar(key = row.key, displayName = row.displayName)

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                row.displayName,
                color = AppColors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${row.owned}/${row.total} · ${AppLocale.illustratorsSets(row.expansionCount)}",
                color = AppColors.textMuted,
                fontSize = 11.sp,
                maxLines = 1,
            )
            FillBar(percent = row.percent, accent = AppColors.purple, modifier = Modifier.fillMaxWidth())
        }

        if (canFollow) {
            IconButton(onClick = onToggleFollow) {
                Icon(
                    imageVector = if (row.isFollowed) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (row.isFollowed) AppLocale.illustratorUnfollow else AppLocale.illustratorFollow,
                    tint = if (row.isFollowed) AppColors.gold else AppColors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun IllustratorListSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SkeletonBlock(modifier = Modifier.fillMaxWidth().height(52.dp), index = 0)
        repeat(7) { i ->
            SkeletonBlock(modifier = Modifier.fillMaxWidth().height(76.dp), shape = RoundedCornerShape(16.dp), index = i + 1)
        }
    }
}
