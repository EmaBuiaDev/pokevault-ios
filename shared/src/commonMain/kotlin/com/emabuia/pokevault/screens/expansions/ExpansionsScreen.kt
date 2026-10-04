package com.emabuia.pokevault.screens.expansions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.emabuia.pokevault.data.Expansion
import com.emabuia.pokevault.data.ExpansionsState
import com.emabuia.pokevault.data.WORKER_BASE_URL
import com.emabuia.pokevault.resources.Res
import com.emabuia.pokevault.resources.expansions_cards
import com.emabuia.pokevault.resources.expansions_error
import com.emabuia.pokevault.resources.expansions_title
import com.emabuia.pokevault.resources.retry
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ExpansionsScreen() {
    val viewModel = koinViewModel<ExpansionsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        ExpansionsState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is ExpansionsState.Error -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(Res.string.expansions_error), style = MaterialTheme.typography.titleMedium)
            Text(current.message, style = MaterialTheme.typography.bodySmall)
            Button(onClick = viewModel::retry, modifier = Modifier.padding(top = 16.dp)) {
                Text(stringResource(Res.string.retry))
            }
        }
        is ExpansionsState.Ready -> ExpansionList(current.expansions)
    }
}

@Composable
private fun ExpansionList(expansions: List<Expansion>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = WindowInsets.safeDrawing.asPaddingValues(),
    ) {
        item {
            Text(
                stringResource(Res.string.expansions_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
        items(expansions, key = { it.id }) { expansion ->
            ExpansionRow(expansion)
            HorizontalDivider()
        }
    }
}

@Composable
private fun ExpansionRow(expansion: Expansion) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = expansion.logoUrl(WORKER_BASE_URL),
            contentDescription = expansion.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(width = 96.dp, height = 48.dp),
        )
        Column(Modifier.padding(start = 16.dp)) {
            Text(expansion.name, style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(
                    expansion.series,
                    stringResource(Res.string.expansions_cards, expansion.cardCount),
                    expansion.releaseDate,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
