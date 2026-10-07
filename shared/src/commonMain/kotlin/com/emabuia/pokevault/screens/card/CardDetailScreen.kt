package com.emabuia.pokevault.screens.card

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import com.emabuia.pokevault.screens.wishlist.WishlistViewModel
import com.emabuia.pokevault.ui.wishlist.WishlistEditorDialog
import com.emabuia.pokevault.ui.wishlist.WishlistPickerDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.emabuia.pokevault.data.Card
import com.emabuia.pokevault.data.CardAttack
import com.emabuia.pokevault.data.WORKER_BASE_URL
import com.emabuia.pokevault.data.model.CardOptions
import com.emabuia.pokevault.ui.components.CardVariants
import com.emabuia.pokevault.ui.components.CollectionActionBar
import com.emabuia.pokevault.ui.components.DetailInfoRow
import com.emabuia.pokevault.util.IllustratorNames
import androidx.compose.material.icons.filled.ChevronRight
import com.emabuia.pokevault.ui.components.FormField
import com.emabuia.pokevault.ui.components.InfoPill
import com.emabuia.pokevault.ui.components.MarketLinkPill
import com.emabuia.pokevault.ui.components.OptionSelector
import com.emabuia.pokevault.ui.components.PrintChoiceChip
import com.emabuia.pokevault.ui.components.QuantityStepper
import com.emabuia.pokevault.ui.components.RaritySymbolIcon
import com.emabuia.pokevault.ui.components.StaticFieldValue
import com.emabuia.pokevault.ui.components.holoFoil
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.ui.theme.TypeColors
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.RarityUtils
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

// Proporzioni di una carta Pokemon: 63 x 88 mm.
private const val CARD_RATIO = 63f / 88f

/**
 * Il dettaglio di una carta del catalogo: immagine grande, prezzo minimo,
 * rarita', PS, tipo e attacchi, illustratore. Le frecce scorrono il set.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    expansionId: String,
    cardId: String,
    onBack: () -> Unit,
    onOpenCard: (expansionId: String, cardId: String) -> Unit,
    onIllustratorClick: (String) -> Unit = {},
) {
    val viewModel = koinViewModel<CardDetailViewModel>(key = "$expansionId/$cardId") {
        parametersOf(expansionId, cardId)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ownership by viewModel.ownership.collectAsStateWithLifecycle()
    val ready = state as? CardDetailState.Ready

    // Il modulo: com'e' la copia che si sta aggiungendo. Parte dalla prima
    // stampa possibile, una copia, Near Mint, italiano (le carte di questo
    // catalogo sono italiane: e' la lingua del set, come su Android).
    var selectedVariant by remember(cardId, ready?.variants) { mutableStateOf(ready?.variants?.firstOrNull() ?: "Normal") }
    var quantity by remember(cardId) { mutableStateOf(1) }
    var selectedCondition by remember(cardId) { mutableStateOf("Near Mint") }
    var selectedLanguage by remember(cardId) { mutableStateOf(CardOptions.LANGUAGES.first()) }
    var confirmRemove by remember(cardId) { mutableStateOf(false) }

    // Il cuore: in quali wishlist sta la carta, come il picker di SetDetailScreen su Android.
    val wishlists = koinViewModel<WishlistViewModel>()
    val wishId = ready?.card?.italianId()
    val inLists = wishId?.let { wishlists.getWishlistIdsForCard(it) }.orEmpty()
    var picking by remember(cardId) { mutableStateOf(false) }
    var creatingList by remember(cardId) { mutableStateOf(false) }
    var wishNote by remember(cardId) { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = AppColors.background,
        bottomBar = {
            if (ready != null && ownership.signedIn) {
                Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))) {
                    CollectionActionBar(
                        isOwned = ownership.isOwned,
                        isLoading = ownership.isSaving,
                        quantity = quantity,
                        onAdd = {
                            viewModel.add(selectedVariant, quantity, selectedCondition, selectedLanguage)
                            quantity = 1
                        },
                        onRemove = { confirmRemove = true },
                    )
                }
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    val title = (state as? CardDetailState.Ready)?.card?.nome.orEmpty()
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = AppColors.textPrimary)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, AppLocale.back, tint = AppColors.textPrimary)
                    }
                },
                actions = {
                    if (ready != null && ownership.signedIn && wishId != null) {
                        IconButton(onClick = { picking = true }, enabled = !wishlists.isLoading || wishlists.wishlists.isNotEmpty()) {
                            Icon(
                                if (inLists.isNotEmpty()) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = AppLocale.wishlistAddToList,
                                tint = if (inLists.isNotEmpty()) AppColors.red else AppColors.textPrimary,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val current = state) {
                CardDetailState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                is CardDetailState.Error -> Text(
                    current.message,
                    color = AppColors.textSecondary,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                is CardDetailState.Ready -> CardDetailContent(current, onOpenCard, onIllustratorClick) {
                    if (!ownership.signedIn) {
                        Text(
                            "Accedi dalla scheda Carte per aggiungerla alla tua collezione.",
                            color = AppColors.textMuted,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                        return@CardDetailContent
                    }
                    ownership.message?.let {
                        Text(
                            it,
                            color = if (it.startsWith("Non salvato")) AppColors.red else AppColors.green,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    (wishlists.errorMessage ?: wishlists.successMessage ?: wishNote)?.let {
                        Text(
                            it,
                            color = if (wishlists.errorMessage != null) AppColors.red else AppColors.green,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    if (ownership.isOwned) {
                        Text(
                            "In collezione: " + ownership.owned.joinToString(" · ") { "${CardVariants.label(it.variant)} x${it.quantity}" },
                            color = AppColors.textSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    AddToCollectionForm(
                        variants = current.variants,
                        ownedVariants = ownership.ownedVariants,
                        selectedVariant = selectedVariant,
                        onVariant = { selectedVariant = it },
                        quantity = quantity,
                        onQuantity = { quantity = it },
                        condition = selectedCondition,
                        onCondition = { selectedCondition = it },
                        language = selectedLanguage,
                        onLanguage = { selectedLanguage = it },
                    )
                }
            }
        }
    }

    if (picking && wishId != null) {
        WishlistPickerDialog(
            wishlists = wishlists.wishlists,
            selectedWishlistIds = inLists,
            canCreateNew = true,
            onDismiss = { picking = false },
            onCreateNewRequested = {
                picking = false
                if (wishlists.canCreateWishlist(wishlists.isPremium)) creatingList = true else wishNote = AppLocale.wishlistFreeLimit
            },
            onConfirmSelection = { selected ->
                picking = false
                wishNote = null
                wishlists.updateCardWishlists(wishId, selected)
            },
        )
    }

    if (creatingList && wishId != null) {
        WishlistEditorDialog(
            onDismiss = { creatingList = false },
            onConfirm = { draft ->
                wishlists.createWishlistAndAddCard(draft, wishId, wishlists.isPremium) { success ->
                    if (success) creatingList = false
                }
            },
            isSaving = wishlists.isSaving,
        )
    }

    if (confirmRemove && ready != null) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Togliere ${ready.card.nome}?") },
            text = {
                Text("Toglie dalla collezione tutte le copie di questa carta (${ownership.owned.sumOf { it.quantity }}), di ogni stampa e lingua. Anche sull'app Android.")
            },
            confirmButton = {
                TextButton(onClick = { confirmRemove = false; viewModel.removeAll() }) {
                    Text("Togli", color = AppColors.red)
                }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Annulla") } },
        )
    }
}

/**
 * Il modulo "com'e' la copia" del dettaglio carta di Android: stampa a
 * pastiglie (con la spunta su quelle gia' possedute), quantita', condizione e
 * lingua.
 */
@Composable
private fun AddToCollectionForm(
    variants: List<String>,
    ownedVariants: Set<String>,
    selectedVariant: String,
    onVariant: (String) -> Unit,
    quantity: Int,
    onQuantity: (Int) -> Unit,
    condition: String,
    onCondition: (String) -> Unit,
    language: String,
    onLanguage: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.card)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FormField(label = AppLocale.printLabel) {
            if (variants.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    variants.forEach { variant ->
                        PrintChoiceChip(
                            variant = variant,
                            selected = variant == selectedVariant,
                            alreadyOwned = variant in ownedVariants,
                            onClick = { onVariant(variant) },
                        )
                    }
                }
            } else {
                StaticFieldValue(text = CardVariants.label(selectedVariant))
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FormField(label = AppLocale.quantity, modifier = Modifier.weight(1f)) {
                QuantityStepper(
                    quantity = quantity,
                    onDecrease = { if (quantity > 1) onQuantity(quantity - 1) },
                    onIncrease = { onQuantity(quantity + 1) },
                )
            }
            FormField(label = AppLocale.condition, modifier = Modifier.weight(1f)) {
                OptionSelector(options = CardOptions.CONDITIONS, selected = condition, onSelect = onCondition)
            }
        }
        FormField(label = AppLocale.languageLabel) {
            OptionSelector(options = CardOptions.LANGUAGES, selected = language, onSelect = onLanguage)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardDetailContent(
    state: CardDetailState.Ready,
    onOpenCard: (String, String) -> Unit,
    onIllustratorClick: (String) -> Unit,
    collectionSection: @Composable () -> Unit,
) {
    val card = state.card
    val uriHandler = LocalUriHandler.current
    val rarity = RarityUtils.getRarityInfo(card.rarity)
    val index = state.neighbours.indexOfFirst { it.cardId == card.cardId }
    val previous = state.neighbours.getOrNull(index - 1)
    val next = state.neighbours.getOrNull(index + 1)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrowseArrow(previous, Icons.AutoMirrored.Filled.KeyboardArrowLeft, onOpenCard)
            AsyncImage(
                model = card.imageUrl(WORKER_BASE_URL, size = "high"),
                contentDescription = card.nome,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .weight(1f)
                    .widthIn(max = 360.dp)
                    .aspectRatio(CARD_RATIO)
                    .clip(RoundedCornerShape(14.dp))
                    .holoFoil(enabled = RarityUtils.hasFoilFinish(card.rarity)),
            )
            BrowseArrow(next, Icons.AutoMirrored.Filled.KeyboardArrowRight, onOpenCard)
        }

        Spacer(Modifier.height(16.dp))
        Text(card.nome, color = AppColors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "${state.expansionName} · #${card.number.orEmpty()}",
            color = AppColors.textSecondary,
            fontSize = 14.sp,
        )

        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.price?.displayText()?.let { InfoPill(icon = "💰", text = it, color = AppColors.green) }
            if (!rarity.isUnknown) {
                InfoPill(
                    icon = "",
                    text = rarity.label,
                    color = if (rarity.adaptive) AppColors.textPrimary else rarity.color,
                    leading = { RaritySymbolIcon(rarity, size = 11.dp) },
                )
            }
            card.tipo?.takeIf { it.isNotBlank() }?.let { InfoPill(icon = "", text = it, color = TypeColors.of(AppLocale.typeToEnglish(it))) }
            card.ps?.takeIf { it.isNotBlank() }?.let { InfoPill(icon = "❤️", text = "$it PS", color = AppColors.red) }
            state.price?.url?.let { url -> MarketLinkPill("Cardmarket") { uriHandler.openUri(url) } }
        }

        collectionSection()

        card.regolaSpeciale?.takeIf { it.isNotBlank() }?.let { rule ->
            Spacer(Modifier.height(16.dp))
            Text(
                rule,
                color = AppColors.textSecondary,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.surface)
                    .padding(12.dp),
            )
        }

        if (card.attacchi.any { it.nome.isNotBlank() }) {
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.surface)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                card.attacchi.filter { it.nome.isNotBlank() }.forEach { AttackRow(it) }
            }
        }

        Spacer(Modifier.height(16.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            card.stage?.takeIf { it.isNotBlank() }?.let { DetailInfoRow("Stadio", stageLabel(it)) }
            card.illustratore?.takeIf { it.isNotBlank() }?.let { raw ->
                // Il nome porta alla pagina dell'artista, come la pastiglia su Android (prima chiave del credito).
                val key = IllustratorNames.keysOf(raw).firstOrNull()
                if (key == null) {
                    DetailInfoRow(AppLocale.illustrator, raw)
                } else {
                    Row(
                        Modifier.fillMaxWidth().clickable { onIllustratorClick(key) },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(AppLocale.illustrator, color = AppColors.textMuted, fontSize = 13.sp)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(raw, color = AppColors.purple, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AppColors.purple, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            card.number?.let { DetailInfoRow("Numero", it) }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun AttackRow(attack: CardAttack) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                attack.nome,
                color = AppColors.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (attack.danno.isNotBlank()) {
                Text(attack.danno, color = AppColors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (attack.descrizione.isNotBlank()) {
            Text(attack.descrizione, color = AppColors.textSecondary, fontSize = 13.sp)
        }
    }
}

@Composable
private fun BrowseArrow(
    target: Card?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onOpenCard: (String, String) -> Unit,
) {
    IconButton(onClick = { target?.let { onOpenCard(it.espansioneId, it.cardId) } }, enabled = target != null) {
        Icon(icon, contentDescription = target?.nome, tint = if (target != null) AppColors.textPrimary else AppColors.textMuted.copy(alpha = 0.3f))
    }
}

/** Lo stadio come lo scrive il catalogo ("Stage1"), come lo legge un giocatore. */
private fun stageLabel(stage: String): String = when (stage.lowercase()) {
    "basic" -> "Base"
    "stage1" -> "Fase 1"
    "stage2" -> "Fase 2"
    else -> stage
}
