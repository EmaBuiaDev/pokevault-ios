package com.emabuia.pokevault.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

/** Lo stesso Worker di produzione dell'app Android: solo GET pubbliche. */
const val WORKER_BASE_URL = "https://pokevault-proxy.pokevault-emanu.workers.dev"

interface CatalogApi {
    suspend fun getExpansions(): List<Expansion>
    suspend fun getExpansionCards(expansionId: String): List<Card>
    suspend fun getExpansionPrices(expansionId: String): Map<String, PriceEntry>
}

class KtorCatalogApi(
    private val client: HttpClient,
    baseUrl: String = WORKER_BASE_URL,
) : CatalogApi {
    private val base = baseUrl.trimEnd('/')

    override suspend fun getExpansions(): List<Expansion> =
        client.get("$base/v1/expansions").body<ExpansionsResponse>().expansions

    override suspend fun getExpansionCards(expansionId: String): List<Card> =
        client.get("$base/v1/expansions/${expansionId.lowercase()}/cards").body<CardsResponse>().cards

    override suspend fun getExpansionPrices(expansionId: String): Map<String, PriceEntry> =
        client.get("$base/ita/prices/${expansionId.lowercase()}.json").body<ExpansionPrices>().prices
}
