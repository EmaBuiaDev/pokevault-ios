package com.emabuia.pokevault.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.ui.theme.AppMotion

/*
 * I pezzi di LabComponents.kt dell'app Android che servono agli illustratori,
 * copiati cosi' come sono. Il resto (collage di copertine, campo di ricerca)
 * o non serve ancora o su iOS sta altrove (LabSearchField.kt).
 */

/**
 * Anello di avanzamento.
 *
 * Il traguardo ha un colore proprio (oro) perche' il 100% e' un evento, non
 * l'ultimo di tanti valori possibili.
 */
@Composable
internal fun ProgressRing(
    percent: Float,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    stroke: Dp = 4.dp,
    accent: Color = AppColors.orange,
    label: String? = null,
    labelSize: Int = 11
) {
    val complete = percent >= 100f
    val color = if (complete) AppColors.gold else accent
    val animated by animateFloatAsState(
        targetValue = (percent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(AppMotion.bar),
        label = "ring"
    )

    Box(contentAlignment = Alignment.Center, modifier = modifier.size(size)) {
        CircularProgressIndicator(
            progress = { 1f },
            modifier = Modifier.size(size),
            color = AppColors.textMuted.copy(alpha = 0.18f),
            strokeWidth = stroke,
            strokeCap = StrokeCap.Round,
            trackColor = Color.Transparent
        )
        CircularProgressIndicator(
            progress = { animated },
            modifier = Modifier.size(size),
            color = color,
            strokeWidth = stroke,
            strokeCap = StrokeCap.Round,
            trackColor = Color.Transparent
        )
        Text(
            text = label ?: "${percent.toInt()}%",
            color = if (complete) AppColors.gold else AppColors.textPrimary,
            fontSize = labelSize.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Barra di riempimento sottile, per le righe di lista. */
@Composable
internal fun FillBar(
    percent: Float,
    modifier: Modifier = Modifier,
    accent: Color = AppColors.orange
) {
    val animated by animateFloatAsState(
        targetValue = (percent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(AppMotion.bar),
        label = "fill"
    )
    LinearProgressIndicator(
        progress = { animated },
        modifier = modifier
            .height(5.dp)
            .clip(CircleShape),
        color = if (percent >= 100f) AppColors.gold else accent,
        trackColor = AppColors.textMuted.copy(alpha = 0.18f),
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {}
    )
}

/** Un numero con la sua etichetta. L'unita' di misura del riassunto in cima. */
@Composable
internal fun StatTile(
    label: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.card)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
        }
        Text(
            text = value,
            color = AppColors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            color = AppColors.textMuted,
            fontSize = 10.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Chip di ordinamento in fila scorrevole.
 *
 * Non usa FilterChip di Material: qui i colori vengono dalla palette dell'app e
 * la fila deve poter scorrere senza andare a capo.
 */
@Composable
internal fun SortChipRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = AppColors.orange
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (selected) accent.copy(alpha = 0.22f) else AppColors.card)
                    .border(
                        1.dp,
                        if (selected) accent else AppColors.textMuted.copy(alpha = 0.22f),
                        CircleShape
                    )
                    .pressScale { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    text = label,
                    color = if (selected) accent else AppColors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

/** Titolo di sezione con la sua scorciatoia a destra. */
@Composable
internal fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = AppColors.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text(actionLabel, color = AppColors.orange, fontSize = 12.sp)
            }
        }
    }
}

/**
 * Il traguardo, quando arriva.
 *
 * Usa la lamina olografica dell'app: e' il linguaggio che il resto di PokeVault
 * usa per dire "questa e' speciale", e un chase chiuso lo e'.
 */
@Composable
internal fun CompletionBanner(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(AppColors.gold.copy(alpha = 0.30f), AppColors.gold.copy(alpha = 0.10f))
                )
            )
            .holoFoil()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.WorkspacePremium,
            contentDescription = null,
            tint = AppColors.gold,
            modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(title, color = AppColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = AppColors.textSecondary, fontSize = 12.sp)
        }
    }
}
