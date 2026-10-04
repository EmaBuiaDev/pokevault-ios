package com.emabuia.pokevault.data

import com.emabuia.pokevault.firebase.AuthTokens
import com.emabuia.pokevault.firebase.FirebaseAuthApi
import com.emabuia.pokevault.firebase.FirestoreApi
import com.emabuia.pokevault.firebase.FirestoreWrites
import com.emabuia.pokevault.firebase.GoogleSignIn
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess

/** Come l'utente conferma di essere lui prima di cancellare l'account. */
sealed interface Reauthentication {
    data class Password(val password: String) : Reauthentication
    data object Google : Reauthentication
}

/**
 * Elimina l'account e tutti i suoi dati: FirebaseAuthManager.deleteAccount di
 * Android, nello stesso ordine.
 *
 * Firebase cancella un account solo con un accesso recente, per questo prima
 * si chiede di nuovo la password (o Google): e si controlla che sia proprio
 * l'account collegato, non un altro.
 */
class AccountDeleter(
    private val auth: AuthRepository,
    private val authApi: FirebaseAuthApi,
    private val firestore: FirestoreApi,
    private val writes: FirestoreWrites,
    private val googleSignIn: GoogleSignIn,
    private val client: HttpClient,
    private val cache: FileCache,
    private val tradeBaseUrl: String = WORKER_BASE_URL,
) {
    /** Con che cosa e' entrato l'utente: chi ha una password la ridigita, gli altri passano da Google. */
    suspend fun usesPassword(): Boolean {
        val token = auth.validIdToken() ?: throw NotSignedInException()
        return "password" in authApi.providers(token)
    }

    suspend fun deleteAccount(confirmation: Reauthentication) {
        val session = auth.session.value ?: throw NotSignedInException()

        // 1. Accesso recente, e dello stesso account.
        val fresh: AuthTokens = when (confirmation) {
            is Reauthentication.Password -> authApi.signIn(session.email, confirmation.password)
            Reauthentication.Google -> authApi.signInWithGoogle(googleSignIn.idToken())
        }
        check(fresh.localId == session.uid) { "Hai confermato con un account diverso da quello collegato" }
        val token = fresh.idToken
        val uid = session.uid

        // 2. Prima il profilo TradeRadar, finche' c'e' l'account per autenticarsi:
        // dopo non ci sarebbe piu' modo di cancellarlo. Se il server non risponde
        // ci si ferma qui, prima di aver cancellato altro, e l'utente riprova.
        deleteTradeProfile(token)

        // 3. Le sottocartelle, le stesse che cancella l'app Android.
        SUBCOLLECTIONS.forEach { collection ->
            val ids = firestore.listDocuments(uid, token, collection).map { it.first }
            writes.deleteDocuments(uid, token, collection, ids)
        }

        // 4. Il profilo, 5. l'account.
        writes.deleteUserDocument(uid, token)
        authApi.deleteAccount(token)

        // 6. Niente resta sul telefono.
        listOf("collection_$uid", "wishlists_$uid").forEach(cache::remove)
        auth.logout()
    }

    private suspend fun deleteTradeProfile(token: String) {
        val response = client.delete("${tradeBaseUrl.trimEnd('/')}/v1/trade/profile") {
            expectSuccess = false
            bearerAuth(token)
        }
        val noProfile = response.status == HttpStatusCode.NotFound && "no_profile" in response.bodyAsText()
        if (!response.status.isSuccess() && !noProfile) {
            throw IllegalStateException("TradeRadar non raggiungibile: riprova tra poco")
        }
    }

    companion object {
        /** FirebaseAuthManager.deleteAccount su Android. */
        val SUBCOLLECTIONS = listOf(
            "cards", "decks", "albums", "wishlists", "match_logs", "tournaments", "goal_albums", "followed_illustrators",
        )
    }
}
