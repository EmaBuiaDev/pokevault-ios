package com.emabuia.pokevault.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.data.formatAmount
import com.emabuia.pokevault.ui.theme.AppColors

/**
 * L'andamento del prezzo Cardmarket in tre punti (30 giorni, 7 giorni, oggi):
 * PriceSparkline di CardDetailBottomSheet su Android. Verde se sale di piu'
 * dell'1%, rosso se scende, grigio se e' fermo.
 */
@Composable
fun PriceSparkline(avg30: Double, avg7: Double, avg1: Double) {
    val points = listOf(avg30, avg7, avg1)
    val labels = listOf("30gg", "7gg", "Oggi")
    val min = points.min()
    val max = points.max()
    val range = (max - min).coerceAtLeast(0.01)

    val trendColor = when {
        avg1 > avg30 * 1.01 -> Color(0xFF22C55E)   // green: rising
        avg1 < avg30 * 0.99 -> Color(0xFFEF4444)   // red: falling
        else -> Color(0xFF9CA3AF)                    // gray: stable
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            val w = size.width
            val h = size.height
            val pad = 12f
            val xStep = (w - 2 * pad) / (points.size - 1)

            val coords = points.mapIndexed { i, v ->
                val x = pad + i * xStep
                val y = (h - pad) - ((v - min) / range * (h - 2 * pad)).toFloat()
                Offset(x, y)
            }

            for (i in 0 until coords.size - 1) {
                drawLine(
                    color = trendColor,
                    start = coords[i],
                    end = coords[i + 1],
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round
                )
            }
            coords.forEach { offset ->
                drawCircle(color = trendColor, radius = 4.5f, center = offset)
                drawCircle(color = Color.Black, radius = 2f, center = offset)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            points.forEachIndexed { i, v ->
                Column(
                    horizontalAlignment = when (i) {
                        0 -> Alignment.Start
                        points.lastIndex -> Alignment.End
                        else -> Alignment.CenterHorizontally
                    }
                ) {
                    Text(labels[i], color = AppColors.textMuted, fontSize = 10.sp)
                    Text("€${formatAmount(v)}", color = AppColors.textSecondary, fontSize = 10.sp)
                }
            }
        }
    }
}
