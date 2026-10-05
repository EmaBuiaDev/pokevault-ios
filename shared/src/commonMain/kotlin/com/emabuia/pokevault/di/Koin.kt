package com.emabuia.pokevault.di

import com.emabuia.pokevault.data.AuthRepository
import com.emabuia.pokevault.data.CatalogApi
import com.emabuia.pokevault.data.CatalogRepository
import com.emabuia.pokevault.data.CollectionRepository
import com.emabuia.pokevault.data.AccountDeleter
import com.emabuia.pokevault.data.CollectionWriter
import com.emabuia.pokevault.data.FileCache
import com.emabuia.pokevault.data.WishlistRepository
import com.emabuia.pokevault.data.PremiumRepository
import com.emabuia.pokevault.data.IllustratorRepository
import com.emabuia.pokevault.data.CollectorRepository
import com.emabuia.pokevault.screens.album.AlbumViewModel
import com.emabuia.pokevault.screens.album.GoalAlbumViewModel
import com.emabuia.pokevault.screens.illustrator.IllustratorViewModel
import com.emabuia.pokevault.screens.graded.GradedCardsViewModel
import com.emabuia.pokevault.data.KtorCatalogApi
import com.emabuia.pokevault.firebase.FirebaseAuthApi
import com.emabuia.pokevault.firebase.FirestoreApi
import com.emabuia.pokevault.firebase.FirestoreWrites
import com.emabuia.pokevault.firebase.GoogleSignIn
import com.emabuia.pokevault.firebase.platformGoogleAuthLauncher
import com.emabuia.pokevault.screens.auth.AuthViewModel
import com.emabuia.pokevault.screens.card.CardDetailViewModel
import com.emabuia.pokevault.screens.cards.ExpansionCardsViewModel
import com.emabuia.pokevault.screens.collection.CollectionViewModel
import com.emabuia.pokevault.screens.expansions.ExpansionsViewModel
import com.emabuia.pokevault.screens.expansions.SearchViewModel
import com.emabuia.pokevault.screens.settings.SettingsViewModel
import com.emabuia.pokevault.screens.stats.StatsViewModel
import com.emabuia.pokevault.screens.wishlist.WishlistViewModel
import com.emabuia.pokevault.ui.theme.ThemePreference
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
    single { GoogleSignIn(get(), platformGoogleAuthLauncher()) }
    single { AuthRepository(get(), get(), get(named(DATA)), now = ::nowMillis) }
    single { CollectionRepository(get(), get(), get(named(CACHE))) }
    single { FirestoreWrites(get()) }
    single { CollectionWriter(get(), get(), get()) }
    single { ThemePreference(get(named(DATA))) }
    single { AccountDeleter(get(), get(), get(), get(), get(), get(), get(named(CACHE))) }
    single { WishlistRepository(get(), get(), get(), get(named(CACHE)), get()) }
    single { PremiumRepository(get(), get(), now = ::nowMillis) }
    single { IllustratorRepository(get(), get(named(CACHE)), get(), get(), get()) }
    single { CollectorRepository(get(), get(), get(named(CACHE)), get()) }
}

val viewModelModule = module {
    factoryOf(::ExpansionsViewModel)
    factoryOf(::SearchViewModel)
    factoryOf(::AuthViewModel)
    factoryOf(::CollectionViewModel)
    factoryOf(::StatsViewModel)
    factoryOf(::WishlistViewModel)
    factoryOf(::SettingsViewModel)
    factoryOf(::IllustratorViewModel)
    factoryOf(::GradedCardsViewModel)
    factoryOf(::AlbumViewModel)
    factoryOf(::GoalAlbumViewModel)
    factory { params -> ExpansionCardsViewModel(expansionId = params.get(), repository = get()) }
    factory { params ->
        CardDetailViewModel(
            expansionId = params.get(0),
            cardId = params.get(1),
            catalog = get(),
            auth = get(),
            collection = get(),
            writer = get(),
        )
    }
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
/** La cartella dati dell'app (sessione, preferenze): non la svuota il sistema. */
const val DATA = "data"

@OptIn(ExperimentalTime::class)
private fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
