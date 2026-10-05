package com.emabuia.pokevault.screens.album

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.viewmodel.koinViewModel
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import com.emabuia.pokevault.util.ChaseCard
import com.emabuia.pokevault.data.Expansion
import com.emabuia.pokevault.data.WORKER_BASE_URL
import com.emabuia.pokevault.ui.premium.PremiumRequiredDialog
import com.emabuia.pokevault.ui.theme.*
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.ImageUrlUtils

@Composable
private fun GoalCardImageFallback(card: ChaseCard, compact: Boolean) {
    val titleSize = if (compact) 7.sp else 9.sp
    val detailSize = if (compact) 6.sp else 7.sp
    val series = "#" + card.number.ifBlank { "-" }
    val setName = card.card.espansioneId.uppercase().ifBlank { "-" }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .background(AppColors.surface)
            .padding(if (compact) 4.dp else 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = card.name,
                color = AppColors.textPrimary,
                fontSize = titleSize,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(1.dp))
            Text(
                text = series,
                color = AppColors.textMuted,
                fontSize = detailSize,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                text = setName,
                color = AppColors.textMuted,
                fontSize = detailSize,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateGoalAlbumScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onPremiumRequired: () -> Unit,
    viewModel: GoalAlbumViewModel = koinViewModel()
) {
    var showPremiumDialog by remember { mutableStateOf(false) }
    var setSearchQuery by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        viewModel.loadAvailableSets()
        viewModel.formCriteriaType = com.emabuia.pokevault.data.model.GoalCriteriaType.SET
        if (!viewModel.canCreate()) {
            showPremiumDialog = true
        }
    }

    // Aggiorna la preview quando cambia criterio
    LaunchedEffect(viewModel.formCriteriaType, viewModel.formCriteriaValue) {
        if (viewModel.formCriteriaValue.isNotBlank()) {
            viewModel.loadPreview()
        }
    }

    Scaffold(
        containerColor = AppColors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(AppLocale.createChaseTitle, color = AppColors.textPrimary, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = AppLocale.back,
                            tint = AppColors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Nome ──────────────────────────────────────────────────────
            OutlinedTextField(
                value = viewModel.formName,
                onValueChange = { viewModel.formName = it },
                label = { Text(AppLocale.chaseNameLabel, color = AppColors.textSecondary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AppColors.orange,
                    unfocusedBorderColor = AppColors.textMuted,
                    focusedTextColor = AppColors.textPrimary,
                    unfocusedTextColor = AppColors.textPrimary,
                    cursorColor = AppColors.orange
                )
            )

            // ── Valore criterio ───────────────────────────────────────────
            Text(AppLocale.set, color = AppColors.textSecondary, fontSize = 13.sp)
            SetPicker(
                sets = viewModel.availableSets,
                selectedValue = viewModel.formCriteriaValue.removeSuffix(GoalAlbumViewModel.ITALIAN_SET_SUFFIX),
                searchQuery = setSearchQuery,
                onSearchChange = { setSearchQuery = it },
                onSelect = { viewModel.formCriteriaValue = GoalAlbumViewModel.italianSetId(it) }
            )

            // ── Preview ───────────────────────────────────────────────────
            if (viewModel.isPreviewLoading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.orange, modifier = Modifier.size(28.dp))
                }
            } else if (viewModel.previewCards.isNotEmpty()) {
                PreviewSection(cards = viewModel.previewCards)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── Salva ─────────────────────────────────────────────────────
            Button(
                onClick = {
                    if (!viewModel.canCreate()) {
                        showPremiumDialog = true
                        return@Button
                    }
                    viewModel.saveGoalAlbum(onSuccess = onSaved)
                },
                enabled = viewModel.formName.isNotBlank()
                    && viewModel.formCriteriaValue.isNotBlank()
                    && !viewModel.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.orange)
            ) {
                if (viewModel.isSaving) {
                    CircularProgressIndicator(color = AppColors.textPrimary, modifier = Modifier.size(20.dp))
                } else {
                    Text(AppLocale.saveChase, color = AppColors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showPremiumDialog) {
        PremiumRequiredDialog(
            title = AppLocale.premiumChaseLimitTitle,
            message = AppLocale.premiumChaseLimitMessage,
            onDismiss = { showPremiumDialog = false; onBack() },
            onUpgrade = {
                showPremiumDialog = false
                onPremiumRequired()
            }
        )
    }
}

// ── Set picker ────────────────────────────────────────────────────────────────

@Composable
private fun SetPicker(
    sets: List<Expansion>,
    selectedValue: String,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSelect: (String) -> Unit
) {
    val selectedSet = remember(selectedValue, sets) {
        sets.firstOrNull { it.id == selectedValue }
    }
    val filtered = remember(searchQuery, sets) {
        val trimmedQuery = searchQuery.trim()
        val matches = if (trimmedQuery.isBlank()) {
            sets
        } else {
            sets.filter { set ->
                listOf(set.name, set.series.orEmpty(), set.id)
                    .any { candidate -> candidate.contains(trimmedQuery, ignoreCase = true) }
            }
        }
        matches.sortedWith(
            compareByDescending<Expansion> { it.id == selectedValue }
                .thenByDescending { it.releaseDate }
                .thenBy { it.name }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text(AppLocale.searchSet, color = AppColors.textMuted, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AppColors.textMuted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppColors.orange,
                unfocusedBorderColor = AppColors.textMuted,
                focusedTextColor = AppColors.textPrimary,
                unfocusedTextColor = AppColors.textPrimary,
                cursorColor = AppColors.orange
            )
        )

        if (selectedSet != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(AppColors.orange.copy(alpha = 0.24f), AppColors.blue.copy(alpha = 0.18f))
                        )
                    )
                    .border(1.dp, AppColors.orange.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    AsyncImage(
                        model = selectedSet.logoUrl(WORKER_BASE_URL),
                        contentDescription = selectedSet.name,
                        modifier = Modifier.size(width = 82.dp, height = 36.dp),
                        contentScale = ContentScale.Fit
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (AppLocale.isItalian) "Set selezionato" else "Selected set",
                            color = AppColors.textMuted,
                            fontSize = 12.sp
                        )
                        Text(
                            text = selectedSet.name,
                            color = AppColors.textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = formatSetMeta(selectedSet),
                            color = AppColors.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (searchQuery.isBlank()) {
                    if (AppLocale.isItalian) "Set disponibili" else "Available sets"
                } else {
                    if (AppLocale.isItalian) "Risultati" else "Results"
                },
                color = AppColors.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = filtered.size.toString(),
                color = AppColors.orange,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(AppColors.orange.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(AppColors.surface)
                    .border(1.dp, AppColors.textMuted.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
                    .padding(18.dp)
            ) {
                Text(
                    text = if (AppLocale.isItalian) {
                        "Nessun set trovato. Prova con nome, serie o codice set."
                    } else {
                        "No set found. Try name, series, or set code."
                    },
                    color = AppColors.textSecondary,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { set ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 })
                    ) {
                        val isSelected = set.id == selectedValue
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (isSelected) AppColors.orange.copy(alpha = 0.14f) else AppColors.surface)
                                .border(
                                    width = if (isSelected) 1.4.dp else 1.dp,
                                    color = if (isSelected) AppColors.orange else AppColors.textMuted.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(18.dp)
                                )
                                .clickable { onSelect(set.id) }
                                .padding(14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.White.copy(alpha = 0.96f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = set.logoUrl(WORKER_BASE_URL),
                                        contentDescription = set.name,
                                        modifier = Modifier.size(28.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = set.name,
                                        color = if (isSelected) AppColors.orange else AppColors.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = formatSetMeta(set),
                                        color = AppColors.textSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = if (isSelected) {
                                        if (AppLocale.isItalian) "Scelto" else "Selected"
                                    } else {
                                        set.id.uppercase()
                                    },
                                    color = if (isSelected) AppColors.textPrimary else AppColors.textMuted,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .background(
                                            if (isSelected) AppColors.orange else Color.White.copy(alpha = 0.06f)
                                        )
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatSetMeta(set: Expansion): String {
    val pieces = buildList {
        set.series?.takeIf { it.isNotBlank() }?.let { add(it) }
        if (set.cardCount > 0) add("${set.cardCount} carte")
        set.releaseDate?.takeIf { it.isNotBlank() }?.let { add(formatSetReleaseDate(it)) }
    }
    return pieces.joinToString(" • ")
}

private fun formatSetReleaseDate(value: String): String {
    val parts = value.split("-")
    return if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else value
}

@Composable
private fun PreviewSection(cards: List<ChaseCard>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "${cards.size} carte target",
            color = AppColors.orange,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.heightIn(max = 160.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            userScrollEnabled = false
        ) {
            itemsIndexed(cards.take(12), key = { _, c -> c.id }) { _, card ->
                SubcomposeAsyncImage(
                    model = card.imageUrl,
                    contentDescription = card.name,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp)),
                    error = { GoalCardImageFallback(card = card, compact = true) }
                )
            }
        }
        if (cards.size > 12) {
            Text(AppLocale.otherCardsCount(cards.size - 12), color = AppColors.textMuted, fontSize = 12.sp)
        }
    }
}
