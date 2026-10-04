package com.emabuia.pokevault.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.ui.theme.ThemeMode
import com.emabuia.pokevault.util.AppLocale
import org.koin.compose.viewmodel.koinViewModel

/**
 * Le impostazioni: le voci dell'app Android che hanno senso su iOS. Premium,
 * sprite della Home e codici regalo arrivano con il Premium.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val viewModel = koinViewModel<SettingsViewModel>()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val delete by viewModel.delete.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    Scaffold(
        containerColor = AppColors.background,
        topBar = {
            TopAppBar(
                title = { Text(AppLocale.settingsTitle, color = AppColors.textPrimary, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, AppLocale.back, tint = AppColors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.background),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            session?.let { current ->
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(current.name, color = AppColors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(current.email, color = AppColors.textMuted, fontSize = 13.sp)
                }
            }

            // Il tema gira fra sistema, chiaro e scuro, come su Android.
            SettingsItem(
                icon = Icons.Default.DarkMode,
                title = AppLocale.themeLabel,
                subtitle = AppLocale.themeSubtitle(themeMode.code),
                onClick = {
                    viewModel.setTheme(
                        when (themeMode) {
                            ThemeMode.SYSTEM -> ThemeMode.LIGHT
                            ThemeMode.LIGHT -> ThemeMode.DARK
                            ThemeMode.DARK -> ThemeMode.SYSTEM
                        }
                    )
                },
            )
            SettingsItem(Icons.Default.PrivacyTip, AppLocale.privacyPolicyLabel, AppLocale.privacyPolicySubtitle) {
                uriHandler.openUri(AppLocale.privacyPolicyUrl)
            }
            SettingsItem(Icons.Default.Description, AppLocale.termsLabel, AppLocale.termsSubtitle) {
                uriHandler.openUri(AppLocale.termsUrl)
            }
            SettingsItem(Icons.Default.MusicNote, AppLocale.tikTokLabel, AppLocale.tikTokSubtitle, accentColor = AppColors.purple) {
                uriHandler.openUri(AppLocale.tikTokUrl)
            }
            if (session != null) {
                SettingsItem(Icons.AutoMirrored.Filled.Logout, AppLocale.logoutLabel, AppLocale.logoutSubtitle, accentColor = AppColors.orange) {
                    viewModel.logout()
                    onBack()
                }
            }

            // Il disclaimer resta in vista: dice anche ad Apple che l'app non e' ufficiale.
            Column(
                Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.card)
                    .padding(16.dp)
            ) {
                Text(AppLocale.disclaimerTitle, color = AppColors.textPrimary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(AppLocale.disclaimerBody, color = AppColors.textMuted, fontSize = 12.sp, lineHeight = 17.sp)
            }

            if (session != null) {
                Text(
                    AppLocale.dangerZone,
                    color = AppColors.red,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 20.dp, top = 8.dp),
                )
                SettingsItem(Icons.Default.DeleteForever, AppLocale.deleteAccountButton, AppLocale.deleteAccountTitle, accentColor = AppColors.red) {
                    viewModel.askDelete()
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    when (delete.step) {
        DeleteStep.NONE -> Unit
        DeleteStep.CONFIRM -> DeleteAccountDialog(
            isDeleting = delete.isBusy,
            error = delete.error,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete,
        )
        DeleteStep.REAUTH_PASSWORD, DeleteStep.REAUTH_GOOGLE -> ReauthenticateDeleteDialog(
            usesPassword = delete.step == DeleteStep.REAUTH_PASSWORD,
            isBusy = delete.isBusy,
            error = delete.error,
            onConfirmPassword = viewModel::deleteWithPassword,
            onConfirmGoogle = viewModel::deleteWithGoogle,
            onDismiss = viewModel::cancelDelete,
        )
    }
}

/** SettingsItem dell'app Android. */
@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color = AppColors.blue,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = accentColor, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = AppColors.textPrimary)
            Text(text = subtitle, fontSize = 12.sp, color = AppColors.textMuted)
        }
        Icon(Icons.Default.ChevronRight, null, tint = AppColors.textMuted, modifier = Modifier.size(20.dp))
    }
    HorizontalDivider(color = AppColors.textMuted.copy(alpha = 0.08f), modifier = Modifier.padding(start = 60.dp))
}

/** DeleteAccountDialog dell'app Android, con l'errore se il passo dopo non parte. */
@Composable
private fun DeleteAccountDialog(isDeleting: Boolean, error: String?, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = { if (!isDeleting) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !isDeleting, dismissOnClickOutside = !isDeleting),
    ) {
        Column(
            modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(AppColors.surface).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.Warning, null, tint = AppColors.red, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(AppLocale.deleteAccountTitle, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.textPrimary, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(12.dp))
            Text(AppLocale.deleteAccountMessage, fontSize = 14.sp, color = AppColors.textSecondary, textAlign = TextAlign.Center, lineHeight = 20.sp)
            error?.let {
                Spacer(modifier = Modifier.height(10.dp))
                Text(it, color = AppColors.red, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            Spacer(modifier = Modifier.height(24.dp))
            if (isDeleting) {
                CircularProgressIndicator(color = AppColors.red, modifier = Modifier.size(32.dp))
            } else {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.red),
                ) { Text(AppLocale.deleteAccountConfirm, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.textSecondary),
                ) { Text(AppLocale.cancel, fontWeight = FontWeight.Medium, fontSize = 15.sp) }
            }
        }
    }
}

/** ReauthenticateDeleteDialog dell'app Android: la password, o Google. */
@Composable
private fun ReauthenticateDeleteDialog(
    usesPassword: Boolean,
    isBusy: Boolean,
    error: String?,
    onConfirmPassword: (String) -> Unit,
    onConfirmGoogle: () -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    Dialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !isBusy, dismissOnClickOutside = !isBusy),
    ) {
        Column(
            modifier = Modifier.clip(RoundedCornerShape(24.dp)).background(AppColors.surface).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Default.Lock, null, tint = AppColors.blue, modifier = Modifier.size(44.dp))
            Text("Conferma identita", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AppColors.textPrimary, textAlign = TextAlign.Center)
            Text(
                "Per motivi di sicurezza, Google richiede un accesso recente prima di eliminare l'account.",
                fontSize = 14.sp,
                color = AppColors.textSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )
            if (usesPassword) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isBusy,
                    singleLine = true,
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
            }
            error?.let { Text(it, color = AppColors.red, fontSize = 13.sp, textAlign = TextAlign.Center) }
            if (isBusy) {
                CircularProgressIndicator(color = AppColors.red, modifier = Modifier.size(32.dp))
                Text(AppLocale.deletingAccount, color = AppColors.textMuted, fontSize = 13.sp)
            } else {
                Button(
                    onClick = { if (usesPassword) onConfirmPassword(password) else onConfirmGoogle() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.red),
                ) { Text(if (usesPassword) "Conferma e elimina" else "Accedi con Google e elimina") }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Text(AppLocale.cancel)
                }
            }
        }
    }
}
