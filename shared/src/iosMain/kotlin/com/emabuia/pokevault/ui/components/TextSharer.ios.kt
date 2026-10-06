package com.emabuia.pokevault.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIPasteboard
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController

@Composable
actual fun rememberTextSharer(): TextSharer = remember { IosTextSharer() }

private class IosTextSharer : TextSharer {
    override fun copy(text: String) {
        UIPasteboard.generalPasteboard.string = text
    }

    /**
     * Il foglio di condivisione di sistema, sopra lo schermo che c'e' adesso.
     * L'oggetto della mail non si passa: il testo comincia gia' col nome del deck.
     */
    override fun share(subject: String, text: String) {
        val presenter = topViewController() ?: return
        val sheet = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
        // Su iPad il foglio e' un popover e vuole un punto d'appoggio, o l'app si chiude.
        sheet.popoverPresentationController?.sourceView = presenter.view
        presenter.presentViewController(sheet, animated = true, completion = null)
    }

    private fun topViewController(): UIViewController? {
        val window = UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
            .firstOrNull { it.isKeyWindow() }
        var top = window?.rootViewController
        while (top?.presentedViewController != null) top = top.presentedViewController
        return top
    }
}
