package com.emabuia.pokevault

import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.window.ComposeUIViewController

// DoNothing: le schermate con campi di testo (l'accesso, portato da Android)
// si scostano gia' dalla tastiera con imePadding. Lasciando il default,
// FocusableAboveKeyboard, iOS spostava in su anche tutta la vista e i due
// spostamenti si sommavano: il modulo finiva fuori schermo.
fun MainViewController() = ComposeUIViewController(
    configure = { onFocusBehavior = OnFocusBehavior.DoNothing },
) { App() }
