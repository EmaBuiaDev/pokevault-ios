package com.emabuia.pokevault.screens.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.AuthRepository
import com.emabuia.pokevault.data.Session
import com.emabuia.pokevault.firebase.FirebaseAuthException
import com.emabuia.pokevault.firebase.FirebaseConfig
import com.emabuia.pokevault.firebase.GoogleSignIn
import com.emabuia.pokevault.firebase.GoogleSignInCancelled
import com.emabuia.pokevault.firebase.GoogleSignInFailed
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

/** Stessi controlli e stessi messaggi di AuthViewModel nell'app Android. */
class AuthViewModel(
    private val repository: AuthRepository,
    private val googleSignIn: GoogleSignIn,
) : ViewModel() {
    val session: StateFlow<Session?> = repository.session

    var uiState by mutableStateOf(AuthUiState())
        private set

    private val validProviders = listOf(
        "gmail.com", "outlook.com", "hotmail.it", "hotmail.com",
        "yahoo.com", "yahoo.it", "icloud.com", "libero.it", "virgilio.it",
        "live.it", "fastwebnet.it", "tiscali.it", "alice.it", "tim.it", "poste.it"
    )

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) return fail("Compila tutti i campi")
        if (!isValidEmail(email)) return fail("Inserisci un'email valida (es. Gmail, Outlook, Libero)")
        launchAuth { repository.login(email.trim(), password) }
    }

    fun register(email: String, password: String, name: String) {
        if (email.isBlank() || password.isBlank() || name.isBlank()) return fail("Compila tutti i campi")
        if (!isValidEmail(email)) return fail("Provider email non supportato o non valido")
        if (password.length < 6) return fail("La password deve avere almeno 6 caratteri")
        launchAuth { repository.register(email.trim(), password, name.trim()) }
    }

    fun resetPassword(email: String) {
        if (email.isBlank()) return fail("Inserisci la tua email")
        launchAuth(successMessage = "Email di reset inviata! Controlla la posta.") { repository.resetPassword(email.trim()) }
    }

    fun logout() = repository.logout()

    /** Come "Continua con Google" su Android: stesso account Google, stesso profilo. */
    fun loginWithGoogle() = launchAuth { repository.loginWithGoogle(googleSignIn.idToken()) }

    fun clearError() {
        uiState = uiState.copy(errorMessage = null)
    }

    private fun fail(message: String) {
        uiState = uiState.copy(errorMessage = message)
    }

    private fun launchAuth(successMessage: String? = null, block: suspend () -> Unit) {
        if (!FirebaseConfig.isConfigured) return fail("Accesso non configurato in questa build.")
        viewModelScope.launch {
            uiState = AuthUiState(isLoading = true)
            uiState = try {
                block()
                AuthUiState(errorMessage = successMessage)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Pagina di Google chiusa: e' una scelta, non un errore da mostrare.
                if (e is GoogleSignInCancelled) AuthUiState()
                else                 AuthUiState(errorMessage = messageFor(e))
            }
        }
    }

    private fun isValidEmail(email: String): Boolean {
        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
        if (!email.trim().matches(emailRegex)) return false
        val domain = email.trim().substringAfterLast("@").lowercase()
        return validProviders.any { domain == it || domain.endsWith(".$it") }
    }

    companion object {
        /** I codici della REST API, con i messaggi che l'app Android da' per gli stessi casi. */
        fun messageFor(error: Throwable): String = when ((error as? FirebaseAuthException)?.code) {
            "EMAIL_EXISTS" -> "Questa email è già registrata"
            // Con la protezione dall'enumerazione delle email Firebase non dice
            // piu' quale dei due e' sbagliato.
            "INVALID_LOGIN_CREDENTIALS", "INVALID_PASSWORD" -> "Email o password non corretta"
            "EMAIL_NOT_FOUND" -> "Nessun account trovato con questa email"
            "INVALID_EMAIL" -> "Formato email non valido"
            "WEAK_PASSWORD" -> "La password deve avere almeno 6 caratteri"
            "TOO_MANY_ATTEMPTS_TRY_LATER" -> "Troppi tentativi. Riprova tra qualche minuto."
            "USER_DISABLED" -> "Questo account è stato disattivato"
            null -> when (error) {
                is UnsupportedOperationException, is GoogleSignInFailed -> error.message ?: "Accesso non riuscito"
                // Solo i veri errori di rete diventano "connessione": prima lo erano
                // tutti, e un rifiuto di Google sembrava un problema di Wi-Fi.
                is kotlinx.io.IOException -> "Errore di connessione. Controlla internet."
                else -> "Accesso non riuscito: ${error.message ?: error::class.simpleName}"
            }
            else -> "Errore: ${error.message}"
        }
    }
}
