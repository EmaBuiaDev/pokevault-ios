package com.emabuia.pokevault.ui.premium

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.ui.theme.AppColors

/**
 * Il limite dell'account gratuito: PremiumRequiredDialog di Android, stessa
 * firma. Su iOS l'acquisto non c'e' ancora (arrivera' con StoreKit), quindi
 * niente pulsante per passare a Premium: Apple non accetta inviti a comprare
 * fuori dall'app. [onUpgrade] resta per quando ci sara'.
 */
@Composable
fun PremiumRequiredDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onUpgrade: () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.surface,
        shape = RoundedCornerShape(24.dp),
        icon = { Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = AppColors.gold, modifier = Modifier.size(40.dp)) },
        title = { Text(title, color = AppColors.textPrimary, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) },
        text = { Text(message, color = AppColors.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK", color = AppColors.gold, fontWeight = FontWeight.SemiBold) } },
    )
}
