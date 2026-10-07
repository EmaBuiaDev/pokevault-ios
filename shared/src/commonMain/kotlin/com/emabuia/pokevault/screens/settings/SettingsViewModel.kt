package com.emabuia.pokevault.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.emabuia.pokevault.data.AccountDeleter
import com.emabuia.pokevault.data.AuthRepository
import com.emabuia.pokevault.data.PremiumRepository
import com.emabuia.pokevault.data.Reauthentication
import com.emabuia.pokevault.data.Session
import com.emabuia.pokevault.firebase.FirebaseAuthException
import com.emabuia.pokevault.firebase.GoogleSignInCancelled
import com.emabuia.pokevault.screens.auth.AuthViewModel
import com.emabuia.pokevault.ui.theme.HomeSpritePreference
import com.emabuia.pokevault.ui.theme.ThemeMode
import com.emabuia.pokevault.ui.theme.ThemePreference
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** I due passi dell'eliminazione: la conferma, poi la verifica dell'identita'. */
enum class DeleteStep { NONE, CONFIRM, REAUTH_PASSWORD, REAUTH_GOOGLE }

data class DeleteState(
    val step: DeleteStep = DeleteStep.NONE,
    val isBusy: Boolean = false,
    val error: String? = null,
)

class SettingsViewModel(
    private val auth: AuthRepository,
    private val deleter: AccountDeleter,
    private val theme: ThemePreference,
    private val premium: PremiumRepository,
    private val homeSprite: HomeSpritePreference,
) : ViewModel() {
    val session: StateFlow<Session?> = auth.session
    val themeMode: StateFlow<ThemeMode> = theme.mode
    val selectedHomeSpriteId: StateFlow<Int> = homeSprite.selectedId

    // PremiumManager.isPremium e giftUntilMs su Android: qui si leggono dal Worker.
    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()
    private val _giftUntilMs = MutableStateFlow(0L)
    val giftUntilMs: StateFlow<Long> = _giftUntilMs.asStateFlow()

    init {
        if (auth.session.value != null) {
            viewModelScope.launch { _isPremium.value = premium.isPremium() == true }
            viewModelScope.launch { _giftUntilMs.value = premium.giftUntilMs() }
        }
    }

    /** canChooseHomeSprite di PremiumManager: solo per chi e' Premium. */
    fun canChooseHomeSprite(): Boolean = _isPremium.value

    fun setHomeSprite(spriteId: Int) = homeSprite.set(spriteId)

    private val _delete = MutableStateFlow(DeleteState())
    val delete: StateFlow<DeleteState> = _delete.asStateFlow()

    fun setTheme(mode: ThemeMode) = theme.set(mode)

    fun logout() = auth.logout()

    fun askDelete() {
        _delete.value = DeleteState(step = DeleteStep.CONFIRM)
    }

    fun cancelDelete() {
        if (!_delete.value.isBusy) _delete.value = DeleteState()
    }

    /** Dopo "Elimina definitivamente": chi ha la password la ridigita, gli altri passano da Google. */
    fun confirmDelete() {
        viewModelScope.launch {
            _delete.value = _delete.value.copy(isBusy = true, error = null)
            _delete.value = try {
                DeleteState(step = if (deleter.usesPassword()) DeleteStep.REAUTH_PASSWORD else DeleteStep.REAUTH_GOOGLE)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                DeleteState(step = DeleteStep.CONFIRM, error = "Impossibile continuare: ${e.message}")
            }
        }
    }

    fun deleteWithPassword(password: String) {
        if (password.isBlank()) {
            _delete.value = _delete.value.copy(error = "Inserisci la password")
            return
        }
        performDelete(Reauthentication.Password(password))
    }

    fun deleteWithGoogle() = performDelete(Reauthentication.Google)

    private fun performDelete(confirmation: Reauthentication) {
        val step = _delete.value.step
        viewModelScope.launch {
            _delete.value = DeleteState(step = step, isBusy = true)
            _delete.value = try {
                deleter.deleteAccount(confirmation)
                DeleteState() // sessione chiusa: l'app torna alla schermata di accesso
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                when (e) {
                    is GoogleSignInCancelled -> DeleteState(step = step)
                    is FirebaseAuthException -> DeleteState(step = step, error = AuthViewModel.messageFor(e))
                    else -> DeleteState(step = step, error = e.message ?: "Eliminazione non riuscita")
                }
            }
        }
    }
}
