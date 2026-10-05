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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.emabuia.pokevault.data.Card
import com.emabuia.pokevault.data.WORKER_BASE_URL
import com.emabuia.pokevault.data.WishlistCard
import com.emabuia.pokevault.data.WishlistContent
import com.emabuia.pokevault.data.model.Wishlist
import com.emabuia.pokevault.ui.components.formatEur
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.ui.wishlist.WishlistBadge
import com.emabuia.pokevault.ui.wishlist.WishlistEditorDialog
import com.emabuia.pokevault.ui.wishlist.wishlistAccentColor
import com.emabuia.pokevault.util.AppLocale
import org.koin.compose.viewmodel.koinViewModel

/**
 * Le wishlist dell'account: l'elenco, e toccando una lista le sue carte col
 * prezzo minimo. Si creano, si modificano, si eliminano e se ne tolgono carte
 * come su Android (WishlistListScreen e WishlistDetailScreen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreen(onBack: () -> Unit, onCardClick: (Card) -> Unit) {
    val viewModel = koinViewModel<WishlistViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val open = state.lists.firstOrNull { it.wishlist.id == openId }

    var creating by remember { mutableStateOf(false) }
    var showLimit by remember { mutableStateOf(false) }
    var toEdit by remember { mutableStateOf<Wishlist?>(null) }
    var toDelete by remember { mutableStateOf<Wishlist?>(null) }
    var toRemove by remember { mutableStateOf<Pair<String, WishlistCard>?>(null) }
    val snackbar = remember { SnackbarHostState() }

    val requestCreate = { if (state.canCreate) creating = true else showLimit = true }

    LaunchedEffect(state.saveError) {
        state.saveError?.let {
            snackbar.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        containerColor = AppColors.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(open?.wishlist?.name ?: AppLocale.wishlistTitle, color = AppColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { if (open != null) openId = null else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, AppLocale.back, tint = AppColors.textPrimary)
                    }
                },
                actions = {
                    if (open != null) {
                        ListMenu(onEdit = { toEdit = open.wishlist }, onDelete = { toDelete = open.wishlist }, tint = AppColors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background),
            )
        },
        floatingActionButton = {
            if (open == null && !state.isLoading && state.errorMessage == null) {
                FloatingActionButton(onClick = requestCreate, containerColor = AppColors.purple, shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Default.Add, contentDescription = AppLocale.wishlistCreate, tint = AppColors.onAccent)
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.errorMessage != null -> Text(state.errorMessage!!, color = AppColors.red, modifier = Modifier.padding(20.dp))
                open != null -> WishlistCards(open, onCardClick, onRemove = { toRemove = open.wishlist.id to it })
                state.lists.isEmpty() -> EmptyMessage(AppLocale.wishlistEmpty, AppLocale.wishlistEmptySubtitle)
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.lists, key = { it.wishlist.id }) { content ->
                        WishlistRow(
                            content,
                            onClick = { openId = content.wishlist.id },
                            onEdit = { toEdit = content.wishlist },
                            onDelete = { toDelete = content.wishlist },
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        WishlistEditorDialog(
            onDismiss = { creating = false },
            onConfirm = { draft -> viewModel.create(draft) { creating = false } },
            isSaving = state.isSaving,
        )
    }

    toEdit?.let { list ->
        WishlistEditorDialog(
            onDismiss = { toEdit = null },
            onConfirm = { draft -> viewModel.update(list.id, draft) { toEdit = null } },
            isSaving = state.isSaving,
            initial = list,
            titleText = AppLocale.wishlistEdit,
            confirmText = AppLocale.save,
        )
    }

    toDelete?.let { list ->
        ConfirmDialog(
            title = AppLocale.wishlistDeleteTitle,
            name = list.name,
            message = AppLocale.wishlistDeleteMessage,
            confirm = AppLocale.delete,
            onConfirm = {
                viewModel.delete(list.id)
                if (openId == list.id) openId = null
                toDelete = null
            },
            onDismiss = { toDelete = null },
        )
    }

    toRemove?.let { (listId, item) ->
        ConfirmDialog(
            title = AppLocale.wishlistRemoveCardTitle,
            name = item.card.nome,
            message = null,
            confirm = AppLocale.removeFromCollection,
            onConfirm = {
                viewModel.removeCard(listId, item.storedId)
                toRemove = null
            },
            onDismiss = { toRemove = null },
        )
    }

    if (showLimit) {
        AlertDialog(
            onDismissRequest = { showLimit = false },
            containerColor = AppColors.surface,
            title = { Text(AppLocale.wishlistCreate, color = AppColors.textPrimary) },
            text = { Text(AppLocale.wishlistFreeLimit, color = AppColors.textSecondary) },
            confirmButton = { TextButton(onClick = { showLimit = false }) { Text("OK", color = AppColors.purple) } },
        )
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    name: String,
    message: String?,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.surface,
        title = { Text(title, color = AppColors.textPrimary) },
        text = {
            Column {
                Text(name, color = AppColors.textPrimary, fontWeight = FontWeight.SemiBold)
                if (message != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(message, color = AppColors.textSecondary)
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = AppColors.red) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(AppLocale.cancel, color = AppColors.textMuted) } },
    )
}

/** I tre puntini della riga (e del titolo, a lista aperta): modifica o elimina. */
@Composable
private fun ListMenu(onEdit: () -> Unit, onDelete: () -> Unit, tint: androidx.compose.ui.graphics.Color = AppColors.textMuted) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = AppLocale.wishlistEdit, tint = tint)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(AppLocale.wishlistEdit, color = AppColors.textPrimary) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = AppColors.textMuted) },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text(AppLocale.delete, color = AppColors.red) },
                leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = AppColors.red) },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun EmptyMessage(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = AppColors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = AppColors.textMuted, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun WishlistRow(content: WishlistContent, onClick: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val list = content.wishlist
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.card)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WishlistBadge(iconKey = list.iconKey, accentKey = list.resolvedAccentKey)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(list.name, color = AppColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(AppLocale.wishlistCardsCount(list.cardIds.size), color = AppColors.textMuted, fontSize = 12.sp)
            if (list.budgetEur > 0) {
                BudgetBar(spent = content.totalValue, budget = list.budgetEur, accent = wishlistAccentColor(list.resolvedAccentKey))
            }
        }
        Text(formatEur(content.totalValue), color = AppColors.green, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        ListMenu(onEdit = onEdit, onDelete = onDelete)
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
        Text("${AppLocale.wishlistBudget} ${formatEur(budget)}", color = AppColors.textMuted, fontSize = 11.sp)
    }
}

@Composable
private fun WishlistCards(content: WishlistContent, onCardClick: (Card) -> Unit, onRemove: (WishlistCard) -> Unit) {
    if (content.cards.isEmpty() && content.unresolved == 0) {
        EmptyMessage(AppLocale.wishlistCardsEmpty, AppLocale.wishlistCardsEmptySubtitle)
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(110.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text(
                    "${AppLocale.wishlistCardsCount(content.cards.size)} · ${formatEur(content.totalValue)}",
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
                Box {
                    AsyncImage(
                        model = item.card.imageUrl(WORKER_BASE_URL),
                        contentDescription = item.card.nome,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth().aspectRatio(63f / 88f).clip(RoundedCornerShape(6.dp)),
                    )
                    // La X in alto a destra, come il pulsante rimuovi di WishlistDetailScreen.
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(AppColors.background.copy(alpha = 0.85f))
                            .clickable { onRemove(item) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Close, contentDescription = AppLocale.wishlistRemoveCardTitle, tint = AppColors.textPrimary, modifier = Modifier.size(16.dp))
                    }
                }
                Text(item.card.nome, color = AppColors.textPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                item.price?.displayText()?.let { Text(it, color = AppColors.green, fontSize = 12.sp) }
            }
        }
    }
}
