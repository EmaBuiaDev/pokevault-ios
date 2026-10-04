package com.emabuia.pokevault.di

import com.emabuia.pokevault.data.CatalogApi
import com.emabuia.pokevault.data.CatalogRepository
import com.emabuia.pokevault.data.FileCache
import com.emabuia.pokevault.data.KtorCatalogApi
import com.emabuia.pokevault.screens.cards.ExpansionCardsViewModel
import com.emabuia.pokevault.screens.expansions.ExpansionsViewModel
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

fun dataModule(cacheDir: String) = module {
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
    single { FileCache(cacheDir) }
    single { CatalogRepository(get(), get()) }
}

val viewModelModule = module {
    factoryOf(::ExpansionsViewModel)
    factory { params -> ExpansionCardsViewModel(expansionId = params.get(), repository = get()) }
}

/** [cacheDir]: la cartella delle cache della piattaforma, dove il catalogo resta fra un avvio e l'altro. */
fun initKoin(cacheDir: String) {
    startKoin {
        modules(
            dataModule(cacheDir),
            viewModelModule,
        )
    }
}
