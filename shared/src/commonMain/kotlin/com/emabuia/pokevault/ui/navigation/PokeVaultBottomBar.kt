package com.emabuia.pokevault.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CatchingPokemon
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.ui.theme.AppMotion

/**
 * Le quattro sezioni della bottom bar, le stesse dell'app Android.
 * TradeRadar (il tasto al centro) e il FAB dello scanner arrivano con le loro funzioni.
 */
enum class BottomTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    CARDS("Carte", Icons.Default.Style),
    POKEDEX("Pokédex", Icons.Default.CatchingPokemon),
    STATS("Stats", Icons.Default.BarChart),
}

/** Riga delle quattro voci. */
private val BottomBarRowHeight = 72.dp

/** Filo di separazione sopra la riga. */
private val BottomBarHairline = 1.dp

/**
 * Barra di navigazione principale, portata da PokeVaultBottomBar dell'app Android.
 *
 * Non e' la `NavigationBar` di Material3: quella disegna un suo indicatore a
 * pillola dietro l'icona e non lascia spazio alla barretta che scivola da una
 * voce all'altra. Qui la struttura e' una Row di voci a peso uguale con
 * l'indicatore in un livello sopra, posizionato per offset.
 */
@Composable
fun PokeVaultBottomBar(
    selected: BottomTab?,
    onSelect: (BottomTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val slots = BottomTab.entries
    // Il divisorio segue il testo invece di essere bianco fisso: su tema chiaro
    // un bianco al 7% sopra una surface bianca non si vedrebbe.
    val hairline = AppColors.textPrimary.copy(alpha = 0.07f)

    // La striscia di sistema sotto la riga: su iPhone l'indicatore Home.
    val bottomInset = WindowInsets.safeDrawing
        .only(WindowInsetsSides.Bottom)
        .asPaddingValues()
        .calculateBottomPadding()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(AppColors.surface)
    ) {
        val tabWidth = maxWidth / slots.size
        val indicatorOffset by animateDpAsState(
            targetValue = tabWidth * slots.indexOf(selected).coerceAtLeast(0),
            animationSpec = AppMotion.landing(),
            label = "bottomBarIndicatorOffset"
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BottomBarHairline)
                    .background(hairline)
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                if (selected != null) {
                    Box(
                        modifier = Modifier
                            .offset(x = indicatorOffset)
                            .width(tabWidth)
                            .height(3.dp)
                            .background(AppColors.blue)
                    )
                }

                // La riga arriva fino al bordo dello schermo, e sono le voci a
                // scansare la barra di sistema dentro di se': un dito appoggiato
                // in basso, dove si tocca una bottom bar, deve trovare una voce.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(BottomBarRowHeight + bottomInset)
                ) {
                    slots.forEach { tab ->
                        BottomBarItem(
                            tab = tab,
                            isSelected = tab == selected,
                            onClick = { onSelect(tab) },
                            contentBottomPadding = bottomInset,
                            // weight e non width(tabWidth): larghezze arrotondate
                            // ognuna per conto suo lascerebbero una cucitura che
                            // non risponde. tabWidth resta all'indicatore.
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    tab: BottomTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = AppMotion.pressSpring(),
        label = "bottomBarItemScale"
    )
    val tint by animateColorAsState(
        targetValue = if (isSelected) AppColors.blue else AppColors.textMuted,
        animationSpec = tween(AppMotion.state),
        label = "bottomBarItemTint"
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interaction,
                indication = null
            )
            // Il padding viene dopo il selectable di proposito: l'area che
            // risponde resta alta quanto la voce, fino al bordo dello schermo.
            .padding(bottom = contentBottomPadding)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically)
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = tab.label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = tint,
            maxLines = 1
        )
    }
}
