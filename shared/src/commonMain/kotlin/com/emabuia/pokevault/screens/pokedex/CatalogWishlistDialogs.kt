package com.emabuia.pokevault.screens.pokedex

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.emabuia.pokevault.data.remote.TcgCard
import com.emabuia.pokevault.ui.premium.PremiumRequiredDialog
import com.emabuia.pokevault.screens.wishlist.WishlistViewModel
import com.emabuia.pokevault.ui.wishlist.WishlistEditorDialog
import com.emabuia.pokevault.ui.wishlist.WishlistPickerDialog
import com.emabuia.pokevault.util.AppLocale

/**
 * Il cuore di una tessera del catalogo: quale dialogo e' aperto, e per che
 * carta. Stessa logica del dettaglio espansione (SetDetailScreen): senza
 * wishlist si crea la prima, altrimenti si sceglie in quali mettere la carta.
 */
@Stable
class CatalogWishlistState {
    var pickerCard by mutableStateOf<TcgCard?>(null)
    var createCard by mutableStateOf<TcgCard?>(null)
    var showPremium by mutableStateOf(false)

    fun open(card: TcgCard, wishlistViewModel: WishlistViewModel) {
        if (wishlistViewModel.wishlists.isEmpty()) createCard = card else pickerCard = card
    }
}

@Composable
fun rememberCatalogWishlistState(): CatalogWishlistState = remember { CatalogWishlistState() }

@Composable
fun CatalogWishlistDialogs(
    state: CatalogWishlistState,
    wishlistViewModel: WishlistViewModel,
    onPremiumRequired: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    // Il Premium lo legge il ViewModel dal Worker, come nel dettaglio espansione.
    val isPremium = wishlistViewModel.isPremium

    state.pickerCard?.let { card ->
        // Da isPremium e non direttamente: all'attivazione del Premium deve ricomporsi.
        val canCreateWishlist = remember(isPremium, wishlistViewModel.wishlists.size) {
            wishlistViewModel.canCreateWishlist(isPremium)
        }
        WishlistPickerDialog(
            wishlists = wishlistViewModel.wishlists,
            selectedWishlistIds = wishlistViewModel.getWishlistIdsForCard(card.id),
            canCreateNew = canCreateWishlist,
            onDismiss = { state.pickerCard = null },
            onCreateNewRequested = {
                if (canCreateWishlist) {
                    state.createCard = card
                    state.pickerCard = null
                } else {
                    state.showPremium = true
                }
            },
            onConfirmSelection = { selectedIds ->
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                wishlistViewModel.updateCardWishlists(card.id, selectedIds)
                state.pickerCard = null
            }
        )
    }

    state.createCard?.let { card ->
        WishlistEditorDialog(
            onDismiss = { state.createCard = null },
            onConfirm = { draft ->
                wishlistViewModel.createWishlistAndAddCard(draft, card.id, isPremium) { success ->
                    if (success) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        state.createCard = null
                    }
                }
            },
            isSaving = wishlistViewModel.isSaving
        )
    }

    if (state.showPremium) {
        PremiumRequiredDialog(
            title = AppLocale.premiumWishlistLimitTitle,
            message = AppLocale.premiumWishlistLimitMessage,
            onDismiss = { state.showPremium = false },
            onUpgrade = {
                state.showPremium = false
                onPremiumRequired()
            }
        )
    }
}
