package com.emabuia.pokevault.di

import com.emabuia.pokevault.data.CatalogApi
import com.emabuia.pokevault.data.CatalogRepository
import com.emabuia.pokevault.data.KtorCatalogApi
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
}

fun initKoin() {
    startKoin {
        modules(
            dataModule,
            viewModelModule,
        )
    }
}
