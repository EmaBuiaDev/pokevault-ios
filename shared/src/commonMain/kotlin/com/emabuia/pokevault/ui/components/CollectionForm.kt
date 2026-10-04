package com.emabuia.pokevault.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale

// Il modulo "aggiungi alla collezione" del dettaglio carta dell'app Android
// (CardDetailBottomSheet): stessi pezzi, resi pubblici per il dettaglio iOS.

@Composable
fun CollectionActionBar(
    isOwned: Boolean,
    isLoading: Boolean,
    quantity: Int,
    onAdd: () -> Unit,
    onRemove: () -> Unit
) {
    val label = when {
        quantity > 1 -> AppLocale.addCopies(quantity)
        isOwned -> AppLocale.addCopy
        else -> AppLocale.addToCollection
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.background)
    ) {
        HorizontalDivider(color = AppColors.textMuted.copy(alpha = 0.12f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { if (!isLoading) onAdd() },
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.blue)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(19.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isOwned) {
                OutlinedButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .width(52.dp)
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(0.dp),
                    border = BorderStroke(1.dp, AppColors.red.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = AppLocale.removeFromCollection,
                        tint = AppColors.red,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/** L'etichetta piccola sopra un controllo del form, sempre della stessa misura. */
@Composable
fun FormField(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = AppColors.textMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        content()
    }
}

// I campi del form (tendine, contatore, pastiglie) stanno dentro un riquadro
// AppColors.card e si disegnano su AppColors.background con questo filo di
// bordo -- NON su AppColors.surface: nel tema chiaro `surface` e `card` sono
// lo stesso bianco, e un campo su `surface` dentro il riquadro sparirebbe,
// lasciando una lastra bianca senza confini. `background` invece stacca dalla
// card in tutti e due i temi, e il bordo la chiude comunque.
private const val FIELD_BORDER_ALPHA = 0.18f

/** Un campo che non si puo' cambiare: la forma di [OptionSelector], senza freccia. */
@Composable
fun StaticFieldValue(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.background)
            .border(1.dp, AppColors.textMuted.copy(alpha = FIELD_BORDER_ALPHA), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 13.dp)
    ) {
        Text(
            text = text,
            color = AppColors.textSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Una stampa fra cui scegliere, misura scheda.
 *
 * Il colore e' quello che la stampa ha gia' in griglia ([CardVariants.color]),
 * cosi' la reverse e' lo stesso azzurro in tutta l'app. Quella che si ha gia'
 * resta scegliibile -- una seconda copia e' legittima -- ma porta la spunta.
 */
@Composable
fun PrintChoiceChip(
    variant: String,
    selected: Boolean,
    alreadyOwned: Boolean,
    onClick: () -> Unit
) {
    val tint = CardVariants.color(variant)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) tint.copy(alpha = 0.22f) else AppColors.background)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) tint else AppColors.textMuted.copy(alpha = FIELD_BORDER_ALPHA),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (alreadyOwned) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = AppLocale.alreadyOwnedPrint,
                tint = AppColors.green,
                modifier = Modifier.size(13.dp)
            )
        }
        Text(
            text = CardVariants.label(variant),
            color = if (selected) AppColors.textPrimary else AppColors.textSecondary,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

/** Il contatore delle copie: meno, numero, piu'. Alto quanto [OptionSelector]. */
@Composable
fun QuantityStepper(
    quantity: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.background)
            .border(1.dp, AppColors.textMuted.copy(alpha = FIELD_BORDER_ALPHA), RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        StepperButton(
            icon = Icons.Default.Remove,
            contentDescription = "-",
            enabled = quantity > 1,
            // Una velatura del colore del testo, non una tinta fissa: cosi'
            // schiarisce col tema scuro e scurisce con quello chiaro, e il
            // tasto si vede sopra il campo in tutti e due.
            container = AppColors.textPrimary.copy(alpha = 0.09f),
            tint = AppColors.textPrimary,
            onClick = onDecrease
        )
        Text(
            text = "$quantity",
            color = AppColors.textPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp
        )
        StepperButton(
            icon = Icons.Default.Add,
            contentDescription = "+",
            enabled = true,
            container = AppColors.blue,
            tint = Color.White,
            onClick = onIncrease
        )
    }
}

@Composable
fun StepperButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    container: Color,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (enabled) container else container.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.4f),
            modifier = Modifier.size(17.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OptionSelector(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                // Vedi [FIELD_BORDER_ALPHA]: `surface` qui sparirebbe col tema chiaro.
                .background(AppColors.background)
                .border(1.dp, AppColors.textMuted.copy(alpha = FIELD_BORDER_ALPHA), RoundedCornerShape(12.dp))
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 13.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selected,
                    color = AppColors.textPrimary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = AppColors.textMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(AppColors.surface)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option,
                            color = if (option == selected) AppColors.blue else AppColors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = if (option == selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    onClick = { onSelect(option); expanded = false },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

