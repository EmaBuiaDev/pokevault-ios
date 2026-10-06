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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.emabuia.pokevault.util.AppLocale

/**
 * Le quattro sezioni della bottom bar, le stesse dell'app Android.
 * TradeRadar e' il tasto al centro ([TradeRadarBarButton]); il FAB dello scanner sta in ScannerFab.kt.
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
 * Il tasto TradeRadar al centro della barra, fra Carte e Pokedex.
 * [pending] = le proposte che aspettano te. Null = la barra a quattro voci.
 */
data class TradeRadarBarButton(val pending: Int, val onClick: () -> Unit)

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
    tradeRadar: TradeRadarBarButton? = null,
) {
    // Le posizioni della riga: null e' il posto del tasto TradeRadar.
    val slots: List<BottomTab?> = if (tradeRadar == null) {
        BottomTab.entries
    } else {
        listOf(BottomTab.HOME, BottomTab.CARDS, null, BottomTab.POKEDEX, BottomTab.STATS)
    }
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
                        if (tab == null) {
                            TradeRadarBarItem(
                                button = tradeRadar ?: return@forEach,
                                contentBottomPadding = bottomInset,
                                modifier = Modifier.weight(1f)
                            )
                            return@forEach
                        }
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

/** Diametro del cerchio di TradeRadar e quanto sporge sopra la barra. */
private val TradeRadarButtonSize = 54.dp
private val TradeRadarButtonLift = 18.dp

/**
 * Il tasto TradeRadar: un cerchio verde-blu che sporge sopra la barra, con un
 * radar che gira piano (fermo se le animazioni di sistema sono spente) e il
 * numero delle proposte che aspettano te. Non e' una voce come le altre: apre
 * TradeRadar, che ha la sua schermata, quindi non ha lo stato "selezionato".
 */
@Composable
private fun TradeRadarBarItem(
    button: TradeRadarBarButton,
    contentBottomPadding: Dp,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = AppMotion.pressSpring(),
        label = "tradeRadarButtonScale"
    )
    val description = AppLocale.navTradeRadarDescription(button.pending)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = button.onClick
            )
            .semantics { contentDescription = description }
            .padding(bottom = contentBottomPadding)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = -TradeRadarButtonLift)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            RadarDisc(modifier = Modifier.size(TradeRadarButtonSize))
            if (button.pending > 0) {
                PendingBadge(
                    count = button.pending,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-2).dp)
                )
            }
        }
        // In basso come le scritte delle altre voci (centrate in una riga da 72).
        Text(
            text = AppLocale.navTradeRadar,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.green,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 17.dp)
        )
    }
}

/** Il cerchio col radar: anelli, il raggio che gira e il punto al centro. */
@Composable
private fun RadarDisc(modifier: Modifier = Modifier) {
    val glow = AppColors.green.copy(alpha = 0.45f)
    val ring = AppColors.onAccent
    val angle = if (AppMotion.enabled) {
        val transition = rememberInfiniteTransition(label = "tradeRadarSweep")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
            label = "tradeRadarSweepAngle"
        )
        value
    } else {
        315f
    }
    Box(
        modifier = modifier
            .shadow(elevation = 10.dp, shape = CircleShape, ambientColor = glow, spotColor = glow)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(AppColors.green, AppColors.blue)))
            // Il bordo del colore della barra lo "ritaglia" dalla barra stessa.
            .border(3.dp, AppColors.surface, CircleShape)
            .drawBehind {
                val center = this.center
                val radius = size.minDimension / 2f - 7.dp.toPx()
                val stroke = 1.2.dp.toPx()
                drawCircle(ring.copy(alpha = 0.35f), radius = radius, center = center, style = Stroke(stroke))
                drawCircle(ring.copy(alpha = 0.35f), radius = radius * 0.55f, center = center, style = Stroke(stroke))
                rotate(angle, pivot = center) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            0f to Color.Transparent,
                            0.82f to Color.Transparent,
                            1f to ring.copy(alpha = 0.55f),
                            center = center
                        ),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = true,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2)
                    )
                    drawLine(ring, center, Offset(center.x + radius, center.y), strokeWidth = 1.6.dp.toPx())
                }
                drawCircle(ring, radius = 2.6.dp.toPx(), center = center)
            }
    )
}

/** Il numero rosso sopra il tasto: "9+" oltre nove. */
@Composable
private fun PendingBadge(count: Int, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(AppColors.red)
            .border(2.dp, AppColors.surface, CircleShape)
    ) {
        Text(
            text = if (count > 9) "9+" else count.toString(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1
        )
    }
}
