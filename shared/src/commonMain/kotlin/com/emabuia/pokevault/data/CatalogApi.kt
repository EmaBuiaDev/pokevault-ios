package com.emabuia.pokevault.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

/** Lo stesso Worker di produzione dell'app Android: solo GET pubbliche. */
const val WORKER_BASE_URL = "https://pokevault-proxy.pokevault-emanu.workers.dev"

interface CatalogApi {
    suspend fun getExpansions(): List<Expansion>
}

class KtorCatalogApi(
    private val client: HttpClient,
    private val baseUrl: String = WORKER_BASE_URL,
) : CatalogApi {
    override suspend fun getExpansions(): List<Expansion> =
        client.get("${baseUrl.trimEnd('/')}/v1/expansions").body<ExpansionsResponse>().expansions
}
