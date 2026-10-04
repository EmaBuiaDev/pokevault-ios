package com.emabuia.pokevault.di

import com.emabuia.pokevault.data.AuthRepository
import com.emabuia.pokevault.data.CatalogApi
import com.emabuia.pokevault.data.CatalogRepository
import com.emabuia.pokevault.data.FileCache
import com.emabuia.pokevault.data.KtorCatalogApi
import com.emabuia.pokevault.firebase.FirebaseAuthApi
import com.emabuia.pokevault.firebase.FirestoreApi
import com.emabuia.pokevault.screens.auth.AuthViewModel
import com.emabuia.pokevault.screens.cards.ExpansionCardsViewModel
import com.emabuia.pokevault.screens.expansions.ExpansionsViewModel
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.factoryOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

fun dataModule(cacheDir: String, dataDir: String) = module {
    single {
        HttpClient {
            // Un 404 o un 500 diventa un'eccezione, invece di un JSON d'errore decodificato a meta'.
            expectSuccess = true
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    single<CatalogApi> { KtorCatalogApi(get()) }
    single(named(CACHE)) { FileCache(cacheDir) }
    single { CatalogRepository(get(), get(named(CACHE))) }

    // La sessione sta nei dati dell'app, non nelle cache che il sistema puo' svuotare.
    single(named(DATA)) { FileCache(dataDir) }
    single { FirebaseAuthApi(get()) }
    single { FirestoreApi(get()) }
    single { AuthRepository(get(), get(), get(named(DATA)), now = ::nowMillis) }
}

val viewModelModule = module {
    factoryOf(::ExpansionsViewModel)
    factoryOf(::AuthViewModel)
    factory { params -> ExpansionCardsViewModel(expansionId = params.get(), repository = get()) }
}

/**
 * [cacheDir]: le cache della piattaforma, dove il catalogo resta fra un avvio e l'altro.
 * [dataDir]: i dati dell'app, che il sistema non svuota (la sessione).
 */
fun initKoin(cacheDir: String, dataDir: String) {
    startKoin {
        modules(
            dataModule(cacheDir, dataDir),
            viewModelModule,
        )
    }
}

private const val CACHE = "cache"
private const val DATA = "data"

@OptIn(ExperimentalTime::class)
private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
