package com.emabuia.pokevault.screens.deck

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.data.model.Deck
import com.emabuia.pokevault.screens.competitive.DeckLabViewModel
import com.emabuia.pokevault.ui.premium.PremiumRequiredDialog
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale
import org.koin.compose.viewmodel.koinViewModel

/**
 * Il Deck Lab: ui/deck/DeckLabScreen.kt di Android, la scheda "I Miei Deck".
 *
 * Su iOS per ora: elenco con i filtri, dettaglio, elimina, duplica ed
 * esporta. Mancano ancora l'editor (crea e modifica), l'import da testo e le
 * schede Meta Deck e Win Tournament: le parti qui sotto sono quelle di
 * Android, il resto si aggiunge nei prossimi giri.
 *
 * [onCardClick] riceve l'id del catalogo ("ita:..."), non quello del
 * documento: su iOS il dettaglio di una carta si apre da li'.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckLabScreen(
    onBack: () -> Unit,
    onCardClick: (String) -> Unit = {},
    onNavigateToPremium: () -> Unit = {},
    viewModel: DeckLabViewModel = koinViewModel(),
) {
    // Saveable: tornando dal dettaglio di una carta il filtro resta quello scelto.
    var deckListFilter by rememberSaveable { mutableStateOf(DeckListFilter.ALL) }
    var showDeleteDeckDialog by remember { mutableStateOf(false) }
    var showEditorComingSoon by remember { mutableStateOf(false) }
    var showPremiumDeckDialog by remember { mutableStateOf(false) }
    var showPremiumDeckExportDialog by remember { mutableStateOf(false) }
    var showDeckExportDialog by remember { mutableStateOf(false) }
    var decklistExportText by remember { mutableStateOf("") }
    var decklistExportName by remember { mutableStateOf("") }
    // L'id e non il deck: dopo un salvataggio i deck si rileggono, e il
    // dettaglio deve mostrare la versione nuova.
    var selectedDeckId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedDeck: Deck? = viewModel.decks.firstOrNull { it.id == selectedDeckId }

    Scaffold(
        containerColor = AppColors.background,
        topBar = {
            if (selectedDeck == null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(AppColors.card)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = AppLocale.back, tint = AppColors.textPrimary)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Deck Lab",
                                style = MaterialTheme.typography.headlineMedium,
                                color = AppColors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = AppLocale.deckLabMyDecksSubtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.textMuted
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(if (selectedDeck == null) padding else PaddingValues(0.dp))) {
            when {
                selectedDeck != null -> {
                    DeckDetailView(
                        deck = selectedDeck,
                        // allCards e non ownedCards: un deck di prova ha dentro
                        // carte che non sono in collezione, e senza queste il
                        // dettaglio mostrerebbe un mazzo mezzo vuoto.
                        allOwnedCards = viewModel.allCards,
                        onBack = { selectedDeckId = null },
                        onCardClick = { id -> viewModel.apiCardIdOf(id)?.let(onCardClick) },
                        // L'editor arriva nel prossimo giro: per ora la matita lo dice.
                        onEdit = { showEditorComingSoon = true },
                        // Il cestino non esegue da solo: cancellare un deck non
                        // si annulla, e da un deck di prova si porta via anche
                        // le sue carte.
                        onDelete = { showDeleteDeckDialog = true },
                        onDuplicate = {
                            if (viewModel.canCreateDeck()) {
                                viewModel.duplicateDeck(selectedDeck)
                                selectedDeckId = null
                            } else {
                                showPremiumDeckDialog = true
                            }
                        },
                        onExport = {
                            if (viewModel.canExportDecklist()) {
                                decklistExportText = viewModel.buildPtcgDecklist(selectedDeck)
                                decklistExportName = selectedDeck.name.ifBlank { "Deck" }
                                showDeckExportDialog = true
                            } else {
                                showPremiumDeckExportDialog = true
                            }
                        }
                    )
                }

                viewModel.isLoading && viewModel.decks.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AppColors.blue)
                    }
                }

                viewModel.decks.isEmpty() -> EmptyDecksPlaceholder()

                else -> {
                    // Indice costruito una volta per l'intera lista, invece che
                    // scandito da ogni riga.
                    val ownedById = remember(viewModel.allCards) {
                        viewModel.allCards.associateBy { it.id }
                    }
                    val deckRows = remember(viewModel.decks, deckListFilter) {
                        buildDeckListRows(viewModel.decks, deckListFilter)
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item(key = "deck_filter") {
                            DeckListFilterBar(
                                selected = deckListFilter,
                                decks = viewModel.decks,
                                onSelect = { deckListFilter = it }
                            )
                        }
                        if (deckRows.isEmpty()) {
                            item(key = "deck_filter_empty") {
                                Text(
                                    text = AppLocale.deckFilterEmpty,
                                    color = AppColors.textMuted,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 32.dp)
                                )
                            }
                        }
                        items(
                            deckRows,
                            key = { it.key },
                            contentType = { it::class }
                        ) { row ->
                            when (row) {
                                is DeckListRow.Header -> DeckListSectionHeader(row.label, row.count)
                                is DeckListRow.Item -> DeckItem(
                                    deck = row.deck,
                                    onClick = { selectedDeckId = row.deck.id },
                                    ownedById = ownedById
                                )
                            }
                        }
                    }
                }
            }
        }

        val deckToDelete = selectedDeck
        if (showDeleteDeckDialog && deckToDelete != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDeckDialog = false },
                containerColor = AppColors.card,
                title = {
                    Text(
                        text = AppLocale.deckDeleteTitle,
                        color = AppColors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        Text(
                            text = AppLocale.deckDeleteBody(deckToDelete.name),
                            color = AppColors.textSecondary,
                            fontSize = 13.sp
                        )
                        // Su un deck di prova si perde di piu' di quanto dica
                        // il nome del deck: vale la pena scriverlo prima.
                        if (deckToDelete.deckOnly) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = AppLocale.deckDeleteBodyTestDeck,
                                color = AppColors.yellow,
                                fontSize = 12.sp
                            )
                        }
                    }
                },
                confirmButton = {
                    // Tenere il deck e' la scelta sicura, quindi ha il peso
                    // visivo e sta dove il pollice arriva per primo.
                    Button(
                        onClick = { showDeleteDeckDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.blue),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(AppLocale.cancel, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showDeleteDeckDialog = false
                            viewModel.deleteDeck(deckToDelete.id)
                            selectedDeckId = null
                        }
                    ) {
                        Text(AppLocale.delete, color = AppColors.red)
                    }
                }
            )
        }

        if (showEditorComingSoon) {
            AlertDialog(
                onDismissRequest = { showEditorComingSoon = false },
                containerColor = AppColors.card,
                title = { Text("Modifica in arrivo", color = AppColors.textPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Su iPhone per ora i mazzi si guardano, si duplicano e si esportano. Per crearli e modificarli usa ancora l'app Android: arrivano presto anche qui.",
                        color = AppColors.textSecondary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showEditorComingSoon = false }) { Text("Ok", color = AppColors.blue) }
                }
            )
        }

        if (showPremiumDeckDialog) {
            PremiumRequiredDialog(
                title = AppLocale.premiumDeckLimitTitle,
                message = AppLocale.premiumDeckLimitMessage,
                onDismiss = { showPremiumDeckDialog = false },
                onUpgrade = {
                    showPremiumDeckDialog = false
                    onNavigateToPremium()
                }
            )
        }

        if (showPremiumDeckExportDialog) {
            PremiumRequiredDialog(
                title = AppLocale.premiumDeckExportTitle,
                message = AppLocale.premiumDeckExportMessage,
                onDismiss = { showPremiumDeckExportDialog = false },
                onUpgrade = {
                    showPremiumDeckExportDialog = false
                    onNavigateToPremium()
                }
            )
        }

        if (showDeckExportDialog) {
            DeckExportDialog(
                deckName = decklistExportName,
                decklistText = decklistExportText,
                onDismiss = { showDeckExportDialog = false }
            )
        }
    }
}

@Composable
fun EmptyDecksPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(AppColors.card),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Science, contentDescription = null, tint = AppColors.lavender, modifier = Modifier.size(40.dp))
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Ancora nessun deck",
            color = AppColors.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Inizia a sperimentare nel laboratorio e crea la tua squadra perfetta.",
            color = AppColors.textMuted,
            textAlign = TextAlign.Center,
            fontSize = 13.sp
        )
    }
}
