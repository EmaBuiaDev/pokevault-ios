package com.emabuia.pokevault.data.trade

import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Scarica da OpenStreetMap (Overpass) i luoghi di una zona per TradeRadar:
 * data/trade/OverpassClient.kt di Android.
 *
 * Lo fa il telefono e non il server (da Cloudflare Overpass risponde 521); la
 * richiesta la prepara il server e il filtro lo rifa' lui. Il telefono chiede
 * solo il centro della zona, mai la sua posizione. Overpass e' spesso carico:
 * un 504, o un 200 con un "remark" d'errore, e' un fallimento, e si riprova.
 */
class OverpassClient(
    private val client: HttpClient,
    private val url: String = "https://overpass-api.de/api/interpreter",
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Gli elementi grezzi, o null se Overpass non ha risposto bene. */
    suspend fun fetch(query: String): JsonArray? {
        repeat(ATTEMPTS) { attempt ->
            val result = try {
                val response = client.submitForm(url, formParameters = parameters { append("data", query) }) {
                    expectSuccess = false
                    // Overpass rifiuta (406) le richieste senza un'identificazione.
                    header("User-Agent", USER_AGENT)
                    header("Accept", "application/json")
                }
                if (!response.status.isSuccess()) {
                    null
                } else {
                    val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
                    val remark = root["remark"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (remark.contains("error", ignoreCase = true)) null else (root["elements"] as? JsonArray) ?: JsonArray(emptyList())
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
            if (result != null) return result
            if (attempt < ATTEMPTS - 1) delay(4000L * (attempt + 1))
        }
        return null
    }

    private companion object {
        const val USER_AGENT = "PokeVault-TradeRadar/1.0"
        const val ATTEMPTS = 3
    }
}
