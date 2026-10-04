package com.emabuia.pokevault.data

import com.emabuia.pokevault.firebase.AuthTokens
import com.emabuia.pokevault.firebase.FirebaseAuthApi
import com.emabuia.pokevault.firebase.FirestoreApi
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/** L'utente collegato. Resta sul telefono fra un avvio e l'altro. */
@Serializable
data class Session(
    val uid: String,
    val email: String,
    val name: String,
    val idToken: String,
    val refreshToken: String,
    val expiresAt: Long,
)

/**
 * Accesso e sessione: lo stesso giro di FirebaseAuthManager su Android, sulle
 * REST API di Firebase.
 *
 * La sessione sta in un file nella cartella dati dell'app (non nelle cache,
 * che iOS puo' svuotare: sarebbe un logout a sorpresa). TODO prima della
 * pubblicazione: spostare il refresh token nel Portachiavi di iOS, dove lo
 * tiene l'SDK Firebase.
 */
class AuthRepository(
    private val auth: FirebaseAuthApi,
    private val firestore: FirestoreApi,
    private val store: FileCache,
    private val now: () -> Long,
) {
    private val _session = MutableStateFlow(store.read(KEY_SESSION, Session.serializer())?.data)
    val session: StateFlow<Session?> = _session.asStateFlow()

    private val refreshMutex = Mutex()

    /** Come register() su Android: account, poi il profilo users/{uid} col nome. */
    suspend fun register(email: String, password: String, name: String): Session {
        val tokens = auth.signUp(email, password)
        return start(tokens, nameIfNew = name)
    }

    suspend fun login(email: String, password: String): Session =
        start(auth.signIn(email, password), nameIfNew = "Allenatore")

    suspend fun resetPassword(email: String) = auth.sendPasswordReset(email)

    fun logout() {
        store.remove(KEY_SESSION)
        _session.value = null
    }

    /**
     * Un id token valido per Firestore: quello salvato se ha ancora almeno
     * cinque minuti, altrimenti uno nuovo dal refresh token. Null se non
     * c'e' nessuno collegato o se Firebase ha revocato la sessione.
     */
    suspend fun validIdToken(): String? = refreshMutex.withLock {
        val current = _session.value ?: return null
        if (current.expiresAt - now() > FIVE_MINUTES) return current.idToken
        return try {
            val tokens = auth.refresh(current.refreshToken)
            save(current.copy(idToken = tokens.idToken, refreshToken = tokens.refreshToken, expiresAt = expiry(tokens)))
                .idToken
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Account cancellato o password cambiata altrove: la sessione non vale piu'.
            if (e is com.emabuia.pokevault.firebase.FirebaseAuthException) logout()
            null
        }
    }

    /**
     * Sessione nuova e profilo: se users/{uid} non c'e' lo crea, come fa
     * l'app Android al primo accesso con Google. Il profilo non blocca
     * l'accesso: se Firestore non risponde si riprova al prossimo login.
     */
    private suspend fun start(tokens: AuthTokens, nameIfNew: String): Session {
        var name = tokens.displayName.ifBlank { nameIfNew }
        try {
            val profile = firestore.getProfile(tokens.localId, tokens.idToken)
            if (profile == null) {
                firestore.createProfile(tokens.localId, tokens.idToken, name, tokens.email)
            } else if (profile.name.isNotBlank()) {
                name = profile.name
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
        return save(
            Session(
                uid = tokens.localId,
                email = tokens.email,
                name = name,
                idToken = tokens.idToken,
                refreshToken = tokens.refreshToken,
                expiresAt = expiry(tokens),
            )
        )
    }

    private fun save(session: Session): Session {
        store.write(KEY_SESSION, Session.serializer(), session)
        _session.value = session
        return session
    }

    private fun expiry(tokens: AuthTokens) = now() + (tokens.expiresIn.toLongOrNull() ?: 3600L) * 1000

    private companion object {
        const val KEY_SESSION = "session"
        const val FIVE_MINUTES = 5L * 60 * 1000
    }
}
