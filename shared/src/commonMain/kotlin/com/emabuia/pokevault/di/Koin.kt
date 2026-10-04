package com.emabuia.pokevault.di

import com.emabuia.pokevault.data.CatalogApi
import com.emabuia.pokevault.data.CatalogRepository
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

val dataModule = module {
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
    single { CatalogRepository(get()) }
}

val viewModelModule = module {
    factoryOf(::ExpansionsViewModel)
    factory { params -> ExpansionCardsViewModel(expansionId = params.get(), repository = get()) }
}

fun initKoin() {
    startKoin {
        modules(
            dataModule,
            viewModelModule,
        )
    }
}
