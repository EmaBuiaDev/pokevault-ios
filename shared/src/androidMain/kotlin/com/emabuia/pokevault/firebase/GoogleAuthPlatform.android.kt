package com.emabuia.pokevault.firebase

// L'Android di questo repo serve solo a provare il codice comune: l'accesso
// con Google vero sta nell'app di Play, con le Credentials di Google.
actual fun platformGoogleAuthLauncher(): GoogleAuthLauncher = UnsupportedGoogleAuthLauncher()
