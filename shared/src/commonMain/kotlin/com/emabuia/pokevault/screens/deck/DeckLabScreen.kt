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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.ui.graphics.Color
import com.emabuia.pokevault.screens.competitive.MetaDeckViewModel
import kotlinx.coroutines.launch
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
 * Tutto quello che c'e' su Android: i tuoi deck (elenco, dettaglio, editor,
 * import, esporta) e le schede Meta Deck e Win Tournament da Limitless.
 *
 * [onCardClick] riceve l'id del documento in collezione, come su Android:
 * apre il dettaglio carta della Collezione.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckLabScreen(
    onBack: () -> Unit,
    onCardClick: (String) -> Unit = {},
    onNavigateToPremium: () -> Unit = {},
    viewModel: DeckLabViewModel = koinViewModel(),
    metaDeckViewModel: MetaDeckViewModel = koinViewModel(),
) {
    // Saveable: tornando dal dettaglio di una carta il filtro resta quello scelto.
    var deckListFilter by rememberSaveable { mutableStateOf(DeckListFilter.ALL) }
    var showDeleteDeckDialog by remember { mutableStateOf(false) }
    var showSheet by remember { mutableStateOf(false) }
    var showDiscardDeckDialog by remember { mutableStateOf(false) }
    var showNewDeckSourceDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showPremiumMetaDeckDialog by remember { mutableStateOf(false) }
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val deckLabTabs = listOf(AppLocale.deckLabMyDecks, AppLocale.deckLabMetaDeck, AppLocale.deckLabWinTournament)
    val deckScope = rememberCoroutineScope()

    /** C'e' del lavoro che uno swipe distruggerebbe. */
    fun hasDeckWork(): Boolean =
        viewModel.selectedCardsIds.isNotEmpty() || viewModel.newDeckName.isNotBlank()

    // Uno swipe verso il basso non butta via un deck in costruzione: il gesto
    // viene rifiutato, il pannello torna su e si chiede se chiudere davvero
    // (vedi il commento lungo su Android, rimesso su richiesta il 28/09/2026).
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            if (target == SheetValue.Hidden && hasDeckWork()) {
                showDiscardDeckDialog = true
                false
            } else {
                true
            }
        }
    )

    fun closeDeckSheet() {
        showDiscardDeckDialog = false
        showSheet = false
        viewModel.discardEditingDeck()
    }
    var showPremiumDeckDialog by remember { mutableStateOf(false) }
    var showPremiumDeckExportDialog by remember { mutableStateOf(false) }
    var showDeckExportDialog by remember { mutableStateOf(false) }
    var decklistExportText by remember { mutableStateOf("") }
    var decklistExportName by remember { mutableStateOf("") }
    // L'id e non il deck: dopo un salvataggio i deck si rileggono, e il
    // dettaglio deve mostrare la versione nuova.
    var selectedDeckId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedDeck: Deck? = viewModel.decks.firstOrNull { it.id == selectedDeckId }

    /** Un deck dei meta importato: gli stessi controlli di Android, poi il riepilogo. */
    fun importMetaDeck(deck: com.emabuia.pokevault.data.model.MetaDeck) {
        if (viewModel.canCreateDeck()) {
            viewModel.importFromMetaDeck(deck)
            metaDeckViewModel.selectDeck(null)
            // Il risultato e il passo dopo sono sempre di ImportResultDialog.
        } else {
            metaDeckViewModel.selectDeck(null)
            showPremiumDeckDialog = true
        }
    }

    // Il dettaglio di un deck dei meta a tutto schermo, dalle due schede.
    val metaDeckOpen = metaDeckViewModel.selectedDeck.takeIf { selectedTabIndex == 1 || selectedTabIndex == 2 }
    if (metaDeckOpen != null) {
        MetaDeckDetailView(
            deck = metaDeckOpen,
            onBack = { metaDeckViewModel.selectDeck(null) },
            onImport = { importMetaDeck(metaDeckOpen) }
        )
        return
    }

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
                                text = when (selectedTabIndex) {
                                    0 -> AppLocale.deckLabMyDecksSubtitle
                                    1 -> AppLocale.deckLabMetaDeckSubtitle
                                    else -> AppLocale.deckLabWinTournamentSubtitle
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = AppColors.textMuted
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Tabs: I Miei Deck | Meta Deck | Win Tournament
                    SecondaryTabRow(
                        selectedTabIndex = selectedTabIndex,
                        containerColor = Color.Transparent,
                        contentColor = AppColors.blue,
                        divider = {}
                    ) {
                        deckLabTabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                text = {
                                    Text(
                                        text = title,
                                        fontSize = 13.sp,
                                        fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                selectedContentColor = AppColors.blue,
                                unselectedContentColor = AppColors.textMuted
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (selectedDeck == null && selectedTabIndex == 0) {
              Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                // Bottone Importa
                SmallFloatingActionButton(
                    onClick = {
                        if (viewModel.canCreateDeck()) {
                            showImportDialog = true
                        } else {
                            showPremiumDeckDialog = true
                        }
                    },
                    containerColor = AppColors.purple,
                    contentColor = AppColors.textPrimary,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = AppLocale.importDeck)
                }
                ExtendedFloatingActionButton(
                    onClick = {
                        if (viewModel.canCreateDeck()) {
                            viewModel.resetNewDeckState()
                            // La domanda si fa qui, una volta, invece di tenere
                            // un selettore acceso in cima all'editor.
                            showNewDeckSourceDialog = true
                        } else {
                            showPremiumDeckDialog = true
                        }
                    },
                    containerColor = AppColors.blue,
                    contentColor = AppColors.textPrimary,
                    shape = RoundedCornerShape(16.dp),
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(AppLocale.createNewDeck) }
                )
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
                        onCardClick = onCardClick,
                        onEdit = {
                            viewModel.prepareEdit(selectedDeck)
                            showSheet = true
                        },
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

                selectedTabIndex == 1 -> {
                    // Meta Deck: gli archetipi in classifica.
                    MetaArchetypeSection(
                        viewModel = metaDeckViewModel,
                        onImportDeck = { metaDeck -> importMetaDeck(metaDeck) },
                        onCardClick = { metaDeck ->
                            // Stesso limite della scheda Win Tournament.
                            if (metaDeckViewModel.canViewMetaDeck()) {
                                metaDeckViewModel.consumeMetaDeckView()
                                metaDeckViewModel.selectDeck(metaDeck)
                            } else {
                                showPremiumMetaDeckDialog = true
                            }
                        }
                    )
                }

                selectedTabIndex == 2 -> {
                    // Win Tournament: i tornei con i primi tre.
                    WinTournamentSection(
                        viewModel = metaDeckViewModel,
                        onImportDeck = { metaDeck -> importMetaDeck(metaDeck) },
                        onPremiumRequired = { showPremiumMetaDeckDialog = true }
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

        if (showSheet) {
            ModalBottomSheet(
                onDismissRequest = {
                    if (hasDeckWork()) showDiscardDeckDialog = true else closeDeckSheet()
                },
                sheetState = sheetState,
                containerColor = AppColors.surface,
                // Solo il margine in basso: con quello in alto il pannello
                // oscillava dopo uno swipe veloce (Material3 1.4, vedi Android).
                contentWindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom) },
                dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.textMuted) }
            ) {
                NewDeckBottomSheetContent(
                    viewModel = viewModel,
                    isEditing = viewModel.editingDeckId != null,
                    onRequestClose = {
                        if (hasDeckWork()) showDiscardDeckDialog = true else closeDeckSheet()
                    },
                    onSave = {
                        viewModel.saveDeck {
                            showSheet = false
                            selectedDeckId = null
                        }
                    }
                )
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

        if (showDiscardDeckDialog) {
            AlertDialog(
                onDismissRequest = { showDiscardDeckDialog = false },
                containerColor = AppColors.card,
                title = {
                    Text(
                        text = AppLocale.deckDiscardTitle,
                        color = AppColors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = AppLocale.deckDiscardBody,
                        color = AppColors.textSecondary,
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    // Continuare e' la scelta sicura: sta dove arriva il pollice.
                    Button(
                        onClick = {
                            showDiscardDeckDialog = false
                            // Rete di sicurezza: se il pannello si fosse chiuso
                            // lo stesso, "continua" deve riportare dov'eri.
                            if (!sheetState.isVisible) {
                                deckScope.launch { sheetState.show() }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.blue),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(AppLocale.deckDiscardKeepEditing, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { closeDeckSheet() }) {
                        Text(AppLocale.deckDiscardConfirm, color = AppColors.red)
                    }
                }
            )
        }

        // Deck nuovo: dove finiscono le carte che non possiedi. Chiudere senza
        // scegliere non apre l'editor: e' una rinuncia, non un valore di default.
        if (showNewDeckSourceDialog) {
            DeckCardSourceDialog(
                prompt = AppLocale.deckSourceNewDeckQuestion,
                onChoose = { source ->
                    showNewDeckSourceDialog = false
                    viewModel.chooseDeckCardSource(source)
                    showSheet = true
                },
                onDismiss = { showNewDeckSourceDialog = false }
            )
        }

        // Importa da testo
        if (showImportDialog) {
            DeckImportDialog(
                onDismiss = { showImportDialog = false },
                onImport = { text ->
                    viewModel.importFromText(text)
                    showImportDialog = false
                    // Il passo dopo (mancanti, riepilogo) e' sempre di ImportResultDialog.
                }
            )
        }

        // Import: prima la scelta su dove finiscono le carte che non possiedi,
        // poi il riepilogo. Due momenti diversi, come su Android.
        val importResult = viewModel.importResult
        if (importResult != null) {
            if (viewModel.isImportSourceChoicePending) {
                DeckCardSourceDialog(
                    prompt = AppLocale.deckSourceQuestion(
                        importResult.missingMetaDeckCards.sumOf { it.qty }
                    ),
                    isWorking = viewModel.isAddingMissingCards,
                    onChoose = { source -> viewModel.applyImportCardSource(source) },
                    onSkip = { viewModel.skipMissingCards() },
                    onDismiss = { viewModel.skipMissingCards() }
                )
            } else {
                ImportResultDialog(
                    result = importResult,
                    onDismiss = {
                        viewModel.clearImportResult()
                        if (viewModel.selectedCardsIds.isNotEmpty()) {
                            showSheet = true
                        }
                    }
                )
            }
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

        if (showPremiumMetaDeckDialog) {
            PremiumRequiredDialog(
                title = AppLocale.premiumMetaDeckLimitTitle,
                message = AppLocale.premiumMetaDeckLimitMessage,
                onDismiss = { showPremiumMetaDeckDialog = false },
                onUpgrade = {
                    showPremiumMetaDeckDialog = false
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
