package com.emabuia.pokevault.firebase

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.AuthenticationServices.ASWebAuthenticationSessionErrorCodeCanceledLogin
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

actual fun platformGoogleAuthLauncher(): GoogleAuthLauncher = IosGoogleAuthLauncher()

/**
 * La pagina di Google nel foglio del browser di sistema
 * (ASWebAuthenticationSession): e' il modo che Apple e Google vogliono per
 * l'accesso dalle app, e non serve registrare lo schema nell'Info.plist,
 * perche' la sessione intercetta da se' il ritorno su [callbackScheme].
 */
private class IosGoogleAuthLauncher : GoogleAuthLauncher {
    // Tenuti vivi finche' la pagina e' aperta: ASWebAuthenticationSession non
    // trattiene il proprio presentationContextProvider.
    private var session: ASWebAuthenticationSession? = null
    private val anchor = WindowAnchor()

    override suspend fun authorize(url: String, callbackScheme: String): String =
        suspendCancellableCoroutine { continuation ->
            val pageUrl = NSURL(string = url)
            val authSession = ASWebAuthenticationSession(
                uRL = pageUrl,
                callbackURLScheme = callbackScheme,
            ) { callbackUrl, error ->
                session = null
                val absolute = callbackUrl?.absoluteString
                when {
                    absolute != null -> continuation.resume(absolute)
                    // Foglio chiuso dall'utente: codice ufficiale di Apple, non il testo.
                    error?.code == ASWebAuthenticationSessionErrorCodeCanceledLogin ->
                        continuation.resumeWithException(GoogleSignInCancelled())
                    else -> continuation.resumeWithException(
                        GoogleSignInFailed(error?.localizedDescription ?: "Accesso con Google non riuscito")
                    )
                }
            }
            authSession.presentationContextProvider = anchor
            // Riusa l'accesso a Google gia' fatto in Safari, se c'e'.
            authSession.prefersEphemeralWebBrowserSession = false
            session = authSession
            continuation.invokeOnCancellation { authSession.cancel() }
            if (!authSession.start()) {
                session = null
                continuation.resumeWithException(GoogleSignInFailed("Impossibile aprire la pagina di Google"))
            }
        }
}

private class WindowAnchor : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor =
        UIApplication.sharedApplication.keyWindow ?: UIWindow()
}
