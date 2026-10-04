package com.emabuia.pokevault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.ui.theme.AppColors

// InfoPill, DetailInfoRow e MarketLinkPill da CardDetailBottomSheet dell'app Android.

@Composable
fun InfoPill(
    icon: String,
    text: String,
    color: Color,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    bordered: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.12f))
            .then(
                if (bordered) Modifier.border(1.dp, color.copy(alpha = 0.40f), RoundedCornerShape(20.dp))
                else Modifier
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // `leading` serve al segno di rarita', che e' disegnato su Canvas e
        // dentro una stringa non ci sta.
        if (leading != null) leading() else Text(text = icon, fontSize = 12.sp)
        Text(text = text, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        trailing?.invoke()
    }
}

@Composable
fun DetailInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = AppColors.textMuted, fontSize = 13.sp)
        Text(text = value, color = AppColors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/**
 * Il link a un marketplace, della stessa misura delle pastiglie accanto.
 *
 * E' un [InfoPill] con un bordo e la freccetta in coda: il bordo lo distingue
 * dalle pastiglie che sono solo informazione, perche' questa si tocca.
 */
@Composable
fun MarketLinkPill(label: String, onClick: () -> Unit) {
    InfoPill(
        icon = "",
        text = label,
        color = AppColors.blue,
        bordered = true,
        onClick = onClick,
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = AppColors.blue,
                modifier = Modifier.size(11.dp)
            )
        }
    )
}

