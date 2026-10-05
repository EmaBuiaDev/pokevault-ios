package com.emabuia.pokevault.ui.graded

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.emabuia.pokevault.data.model.CardOptions
import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale
import com.emabuia.pokevault.util.GradedLab

/**
 * Il pannello "Carta gradata" del dettaglio collezione di Android, in una
 * finestra: interruttore, voto ed ente di UNA stampa. Si apre dalle copie in
 * Carte e toccando una slab in Carte gradate.
 *
 * Le validazioni sono quelle di Android e stanno prima del salvataggio: senza
 * voto o senza ente non parte niente, e si dice perche'.
 */
@Composable
fun GradingDialog(
    print: PokemonCard,
    onSave: (isGraded: Boolean, grade: Float?, company: String, onResult: (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var isGraded by remember(print.id) { mutableStateOf(print.isGraded) }
    var gradeText by remember(print.id) { mutableStateOf(print.grade?.takeIf { it > 0f }?.let { GradedLab.formatGrade(it) }.orEmpty()) }
    var company by remember(print.id) { mutableStateOf(print.gradingCompany) }
    var companyMenu by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val grade = parseGrade(gradeText)

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        containerColor = AppColors.surface,
        title = { Text(print.name, color = AppColors.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Stars, contentDescription = null, tint = AppColors.gold, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(AppLocale.gradedCardSection, color = AppColors.textPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(AppLocale.insertInGradedCards, color = AppColors.textMuted, fontSize = 11.sp)
                    }
                    Switch(
                        checked = isGraded,
                        onCheckedChange = {
                            isGraded = it
                            if (it && company.isBlank()) company = "PSA"
                            error = null
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = AppColors.gold),
                    )
                }
                if (isGraded) {
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = gradeText,
                            onValueChange = { input ->
                                gradeText = sanitizeGrade(input) ?: gradeText
                                error = null
                            },
                            label = { Text(AppLocale.gradeLabel, fontSize = 10.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = fieldColors(),
                        )
                        Box(Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = company.ifBlank { "PSA" },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(AppLocale.gradingAgency, fontSize = 10.sp) },
                                trailingIcon = {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AppColors.textMuted)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = fieldColors(),
                            )
                            // Il campo e' di sola lettura: il tocco lo prende questo velo trasparente.
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { companyMenu = true },
                            )
                            DropdownMenu(expanded = companyMenu, onDismissRequest = { companyMenu = false }) {
                                CardOptions.GRADING_COMPANIES.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option, color = AppColors.textPrimary) },
                                        onClick = {
                                            company = option
                                            companyMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = AppColors.red, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving,
                onClick = {
                    when {
                        isGraded && grade == null -> error = AppLocale.gradingGradeRequired
                        isGraded && company.isBlank() -> error = AppLocale.gradingCompanyRequired
                        else -> {
                            saving = true
                            onSave(isGraded, grade, company.ifBlank { "PSA" }) { failure ->
                                saving = false
                                if (failure == null) onDismiss() else error = "Non salvato: $failure"
                            }
                        }
                    }
                },
            ) { Text(AppLocale.save, color = AppColors.gold, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text(AppLocale.cancel, color = AppColors.textMuted) }
        },
    )
}

/**
 * Il campo voto di Android: la virgola diventa punto, un punto solo, solo
 * cifre, e sopra il 10 si ferma a 10. null vuol dire "rifiuta il tasto".
 */
internal fun sanitizeGrade(input: String): String? {
    var dots = 0
    val filtered = input.replace(',', '.').filter {
        if (it == '.') ++dots <= 1 else it.isDigit()
    }
    if (filtered.isEmpty()) return ""
    // "9." e' un 9.5 a meta': si valuta senza il punto finale (che non tutte le
    // piattaforme leggono). Un punto da solo non e' ancora un numero: il tasto non passa.
    val value = filtered.removeSuffix(".").toFloatOrNull() ?: return null
    return if (value > 10f) "10" else filtered
}

/** Il voto scritto, o null se non c'e' un voto valido (0 non e' un voto). */
internal fun parseGrade(text: String): Float? = text.removeSuffix(".").toFloatOrNull()?.takeIf { it > 0f && it <= 10f }

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = AppColors.textPrimary,
    unfocusedTextColor = AppColors.textPrimary,
    focusedLabelColor = AppColors.blue,
    unfocusedLabelColor = AppColors.textMuted,
)
