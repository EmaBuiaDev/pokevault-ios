package com.emabuia.pokevault.ui.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale

/**
 * Il bottone dello Scanner sopra la barra in basso: ScannerFab di
 * PokeVaultBottomBar.kt su Android, con la stessa ombra blu.
 */
@Composable
fun ScannerFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "scannerFabScale"
    )
    val shape = RoundedCornerShape(16.dp)
    val glow = AppColors.blue.copy(alpha = 0.45f)

    FloatingActionButton(
        onClick = onClick,
        containerColor = AppColors.blue,
        contentColor = AppColors.onAccent,
        shape = shape,
        interactionSource = interaction,
        // L'ombra la mette il modifier, colorata di blu: quella di default e'
        // nera e sotto un FAB blu sporca invece di staccarlo.
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            focusedElevation = 0.dp,
            hoveredElevation = 0.dp
        ),
        modifier = modifier
            .size(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(elevation = 10.dp, shape = shape, ambientColor = glow, spotColor = glow)
    ) {
        Icon(imageVector = Icons.Default.CameraAlt, contentDescription = AppLocale.scanCard)
    }
}
