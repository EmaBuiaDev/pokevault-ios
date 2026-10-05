package com.emabuia.pokevault.util

import org.junit.Assert.*
import org.junit.Test

/**
 * Nomi, conti e ordinamenti della sezione illustratori.
 *
 * I casi qui sotto non sono inventati: vengono dai 388 nomi veri che
 * `/v1/illustrators` restituisce sul catalogo di produzione. E' la parte che si
 * sbaglia senza accorgersene, perche' un nome spezzato male non fa fallire
 * niente -- crea solo un illustratore che non esiste, con le sue carte rubate a
 * quello vero.
 */
class IllustratorsTest {

    // ── Separazione dei crediti ───────────────────────────────────────────

    @Test
    fun ilPiuSeparaDueAutori() {
        assertEquals(
            listOf("Shinji Higuchi", "Sachiko Eba"),
            IllustratorNames.credits("Shinji Higuchi + Sachiko Eba")
        )
    }

    @Test
    fun laBarraSeparaDueAutoriEIlRuoloSparisce() {
        assertEquals(
            listOf("Kent Kanetsuna", "Shinji Higuchi"),
            IllustratorNames.credits("Kent Kanetsuna/Direc. Shinji Higuchi")
        )
    }

    /**
     * Il caso che ha fatto cambiare la regola: "&" sembrerebbe il separatore
     * piu' ovvio, ma nel catalogo compare solo dentro questo credito unico.
     * Spezzandolo nascerebbero due illustratori inventati.
     */
    @Test
    fun laECommercialeNonSeparaNiente() {
        assertEquals(
            listOf("Illus. & Direc. The Pokémon Company Art Team"),
            IllustratorNames.credits("Illus. & Direc. The Pokémon Company Art Team")
        )
    }

    @Test
    fun laVirgolaNonSeparaINomiTraslitteratiLaUsano() {
        assertEquals(listOf("Saitou, Kouki"), IllustratorNames.credits("Saitou, Kouki"))
    }

    @Test
    fun nienteIllustratoreNessunCredito() {
        assertEquals(emptyList<String>(), IllustratorNames.credits(null))
        assertEquals(emptyList<String>(), IllustratorNames.credits("   "))
    }

    @Test
    fun gliSpaziMultipliCollassano() {
        assertEquals(listOf("Ken Sugimori"), IllustratorNames.credits("  Ken   Sugimori  "))
    }

    // ── Chiavi ────────────────────────────────────────────────────────────

    @Test
    fun leTreDoppieGrafieDelCatalogoCadonoInsiemeSenzaAlias() {
        assertEquals(IllustratorNames.keyOf("takuyoa"), IllustratorNames.keyOf("Takuyoa"))
        assertEquals(IllustratorNames.keyOf("K. Hoshiba"), IllustratorNames.keyOf("K Hoshiba"))
        assertEquals(IllustratorNames.keyOf("Zu-ka"), IllustratorNames.keyOf("Zu-Ka"))
    }

    @Test
    fun gliAccentiSiRipiegano() {
        assertEquals(IllustratorNames.keyOf("Pokémon"), IllustratorNames.keyOf("Pokemon"))
    }

    @Test
    fun duePersoneDiverseRestanoDueChiavi() {
        assertNotEquals(IllustratorNames.keyOf("Ken Sugimori"), IllustratorNames.keyOf("Kouki Saitou"))
    }

    @Test
    fun unaCartaAQuattroManiContaPerEntrambi() {
        val keys = IllustratorNames.keysOf("Shinji Higuchi + Noriko Takaya")
        assertEquals(2, keys.size)
        assertTrue(IllustratorNames.keyOf("Shinji Higuchi") in keys)
        assertTrue(IllustratorNames.keyOf("Noriko Takaya") in keys)
    }

    @Test
    fun loStessoAutoreRipetutoNelCreditoNonSiSdoppia() {
        assertEquals(1, IllustratorNames.keysOf("Shinji Higuchi/Direc. Shinji Higuchi").size)
    }

    // ── Nome da mostrare ──────────────────────────────────────────────────

    @Test
    fun fraDueGrafieVinceQuellaSuPiuCarte() {
        assertEquals(
            "K. Hoshiba",
            IllustratorNames.bestDisplayName(mapOf("K Hoshiba" to 2, "K. Hoshiba" to 9))
        )
    }

    @Test
    fun aPariMeritoIlNomeMostratoNonBallaFraUnCaricamentoELAltro() {
        val a = IllustratorNames.bestDisplayName(mapOf("Zu-ka" to 3, "Zu-Ka" to 3))
        val b = IllustratorNames.bestDisplayName(mapOf("Zu-Ka" to 3, "Zu-ka" to 3))
        assertEquals(a, b)
    }

    // ── Righe e avanzamento ───────────────────────────────────────────────

    private fun entry(
        key: String,
        total: Int,
        expansions: Int = 1
    ) = IllustratorEntry(
        key = key,
        displayName = key,
        rawNames = listOf(key),
        cardApiIds = (1..total).map { "ita:$key:$it" },
        expansionCount = expansions
    )

    @Test
    fun lAvanzamentoContaSoloLeCarteDiQuellIllustratore() {
        val rows = Illustrators.rows(
            entries = listOf(entry("arita", 4), entry("sugimori", 2)),
            ownedApiIds = setOf("ita:arita:1", "ita:arita:3", "ita:sugimori:1", "ita:altro:9"),
            followedKeys = emptySet()
        )
        assertEquals(2, rows.first { it.key == "arita" }.owned)
        assertEquals(4, rows.first { it.key == "arita" }.total)
        assertEquals(1, rows.first { it.key == "sugimori" }.owned)
    }

    @Test
    fun unIllustratoreSenzaCartePosseduteNonDividePerZero() {
        val row = Illustrators.rows(listOf(entry("vuoto", 0)), emptySet(), emptySet()).single()
        assertEquals(0f, row.percent, 0.001f)
        assertFalse(row.isComplete)
        assertEquals(0, row.missing)
    }

    @Test
    fun completoQuandoLeHaTutte() {
        val row = Illustrators.rows(
            listOf(entry("arita", 2)),
            setOf("ita:arita:1", "ita:arita:2"),
            emptySet()
        ).single()
        assertTrue(row.isComplete)
        assertEquals(100f, row.percent, 0.001f)
    }

    // ── Ordinamenti ───────────────────────────────────────────────────────

    private fun row(
        key: String,
        owned: Int,
        total: Int,
        followed: Boolean = false
    ) = IllustratorRow(
        key = key,
        displayName = key,
        owned = owned,
        total = total,
        expansionCount = 1,
        isFollowed = followed
    )

    @Test
    fun iCompletatiScendonoInFondo() {
        val sorted = Illustrators.sort(
            listOf(row("finito", 10, 10), row("quasi", 8, 10), row("appena", 1, 10)),
            IllustratorSort.CLOSEST
        )
        assertEquals(listOf("quasi", "appena", "finito"), sorted.map { it.key })
    }

    @Test
    fun perNumeroDiCarteVinceChiNeHaDisegnateDiPiu() {
        val sorted = Illustrators.sort(
            listOf(row("piccolo", 0, 5), row("grande", 0, 500)),
            IllustratorSort.CARDS
        )
        assertEquals(listOf("grande", "piccolo"), sorted.map { it.key })
    }

    // ── Ricerca ───────────────────────────────────────────────────────────

    @Test
    fun siCercaSenzaAccentiESenzaMaiuscole() {
        val rows = listOf(row(IllustratorNames.keyOf("Kouki Saitou"), 0, 1))
        assertEquals(1, Illustrators.filter(rows, "KOUKI").size)
        assertEquals(1, Illustrators.filter(rows, "saitou").size)
        assertEquals(0, Illustrators.filter(rows, "sugimori").size)
    }

    @Test
    fun ricercaVuotaNonFiltra() {
        val rows = listOf(row("a", 0, 1), row("b", 0, 1))
        assertEquals(2, Illustrators.filter(rows, "   ").size)
    }

    // ── Solo iOS: l'indice del Worker diventa voci ─────────────────────────

    private fun apiId(imageKey: String): String? =
        Regex("""^([A-Za-z0-9-]+)_IT_([A-Za-z0-9_]+)\.(png|webp)$""").matchEntire(imageKey)?.let { m ->
            val number = m.groupValues[2].let { it.toIntOrNull()?.toString() ?: it }
            "ita:" + m.groupValues[1].lowercase() + ":" + number
        }

    @Test
    fun twoSpellingsBecomeOneEntryWithBothRawNames() {
        val entries = Illustrators.entries(
            listOf("K. Hoshiba" to listOf("SV1_IT_001.png", "SV2_IT_5.png"), "K Hoshiba" to listOf("SV1_IT_002.png")),
            ::apiId,
        )
        val entry = entries.single()
        assertEquals("K. Hoshiba", entry.displayName)
        assertEquals(listOf("K. Hoshiba", "K Hoshiba"), entry.rawNames)
        assertEquals(listOf("ita:sv1:1", "ita:sv2:5", "ita:sv1:2"), entry.cardApiIds)
        // Le espansioni si contano dalle carte, non sommando quelle delle due grafie.
        assertEquals(2, entry.expansionCount)
    }

    @Test
    fun aSharedCreditGivesTheCardsToBothArtists() {
        val entries = Illustrators.entries(listOf("Shinji Higuchi + Sachiko Eba" to listOf("PR_IT_1.png")), ::apiId)
        assertEquals(listOf("shinji higuchi", "sachiko eba"), entries.map { it.key })
        assertTrue(entries.all { it.cardApiIds == listOf("ita:pr:1") })
    }

    @Test
    fun unreadableImageKeysAreLeftOut() {
        val entry = Illustrators.entries(listOf("Arita" to listOf("rotto", "SV1_IT_3.png")), ::apiId).single()
        assertEquals(listOf("ita:sv1:3"), entry.cardApiIds)
    }
}
