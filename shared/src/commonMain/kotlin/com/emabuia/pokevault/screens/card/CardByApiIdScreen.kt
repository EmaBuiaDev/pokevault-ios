package com.emabuia.pokevault.screens.card

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.emabuia.pokevault.data.Card
import com.emabuia.pokevault.data.CatalogRepository
import com.emabuia.pokevault.ui.theme.AppColors
import com.emabuia.pokevault.util.AppLocale
import io.ktor.utils.io.CancellationException
import org.koin.compose.koinInject

/**
 * Il dettaglio di una carta partendo dall'id che sta in collezione, album e
 * chase ("ita:me05:4"). Le schermate portate da Android aprono il dettaglio
 * con quell'id; il dettaglio iOS vuole la carta del catalogo, quindi prima la
 * si cerca. Un id non italiano (vecchio o PokeWallet) qui non si risolve.
 */
@Composable
fun CardByApiIdScreen(
    apiCardId: String,
    onBack: () -> Unit,
    onOpenCard: (expansionId: String, cardId: String) -> Unit,
    onIllustratorClick: (String) -> Unit,
) {
    val catalog = koinInject<CatalogRepository>()
    // null finche' si cerca; poi la carta, o Result.failure se non c'e'.
    val found by produceState<Result<Card>?>(null, apiCardId) {
        value = try {
            catalog.italianCardsById(listOf(apiCardId))[apiCardId]?.let { Result.success(it) }
                ?: Result.failure(NoSuchElementException())
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }
    val card = found?.getOrNull()
    when {
        card != null -> CardDetailScreen(
            expansionId = card.espansioneId,
            cardId = card.cardId,
            onBack = onBack,
            onOpenCard = onOpenCard,
            onIllustratorClick = onIllustratorClick,
        )
        found == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else -> Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Questa carta non e' nel catalogo italiano: sull'app Android si vede.",
                color = AppColors.textSecondary,
            )
            TextButton(onClick = onBack) { Text(AppLocale.back, color = AppColors.purple) }
        }
    }
}
