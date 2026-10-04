package com.emabuia.pokevault.screens.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.emabuia.pokevault.data.Session
import com.emabuia.pokevault.ui.auth.AuthScreen
import org.koin.compose.viewmodel.koinViewModel

/**
 * Le sezioni che vivono nell'account (Carte, Stats): senza sessione mostrano
 * l'accesso, con la sessione il loro contenuto. Il catalogo invece resta
 * aperto a tutti, anche per Apple (linea guida 5.1.1: niente registrazione
 * obbligatoria per cio' che non ne ha bisogno).
 */
@Composable
fun RequireLogin(content: @Composable (session: Session, onLogout: () -> Unit) -> Unit) {
    val viewModel = koinViewModel<AuthViewModel>()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val current = session
    if (current == null) {
        AuthScreen(
            onLogin = viewModel::login,
            onRegister = viewModel::register,
            onGoogleSignIn = viewModel::loginWithGoogle,
            onForgotPassword = viewModel::resetPassword,
            isLoading = viewModel.uiState.isLoading,
            errorMessage = viewModel.uiState.errorMessage,
            onClearError = viewModel::clearError,
        )
    } else {
        content(current, viewModel::logout)
    }
}
