package com.emabuia.pokevault.data

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExpansionTest {
    private val json = Json { ignoreUnknownKeys = true }

    // Due voci vere di /v1/expansions (04/10/2026), con un campo che il client non conosce.
    private val payload = """
        {"expansions":[
          {"id":"me05","name":"Buio Pesto","cardCount":120,"officialCount":84,"sortOrder":100,
           "logoKey":null,"series":"Mega Evolution","baseSetCode":"ME05","releaseDate":"2026-07-17"},
          {"id":"svp","name":"Promo SV","cardCount":200,"officialCount":null,"sortOrder":100,
           "logoKey":null,"series":"Scarlatto e Violetto","baseSetCode":null,"releaseDate":null,"nuovo":1}
        ]}
    """.trimIndent()

    @Test
    fun parsesManifest() {
        val expansions = json.decodeFromString<ExpansionsResponse>(payload).expansions
        assertEquals(2, expansions.size)
        assertEquals("Buio Pesto", expansions[0].name)
        assertEquals(84, expansions[0].officialCount)
        assertNull(expansions[1].officialCount)
    }

    @Test
    fun logoUsesBaseSetCodeOrId() {
        val expansions = json.decodeFromString<ExpansionsResponse>(payload).expansions
        assertEquals("https://x.dev/sets/ME05/image?v=setimg-v5&source=ita", expansions[0].logoUrl("https://x.dev/"))
        assertEquals("https://x.dev/sets/SVP/image?v=setimg-v5&source=ita", expansions[1].logoUrl("https://x.dev"))
    }

    @Test
    fun logoUsesPrintedCodeWhereTheIdHasNone() {
        val caos = Expansion(id = "me04", name = "Caos Nascente", baseSetCode = "ME04")
        val obsidian = Expansion(id = "sv03", name = "Ossidiana Infuocata", baseSetCode = "SV03")
        assertEquals("https://x.dev/sets/CRI/image?v=setimg-v5&source=ita", caos.logoUrl("https://x.dev"))
        assertEquals("https://x.dev/sets/OBF/image?v=setimg-v5&source=ita", obsidian.logoUrl("https://x.dev"))
    }
}
