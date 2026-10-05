package com.emabuia.pokevault.util

import com.emabuia.pokevault.data.model.PokemonCard
import com.emabuia.pokevault.data.model.Timestamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * I conti della sezione Gradate.
 *
 * Tre cose in particolare erano sbagliate in modo invisibile prima che questo
 * file esistesse: il filtro delle carte senza ente non selezionava mai niente,
 * l'ordine dei chip cambiava a ogni snapshot di Firestore, e il voto 10 si
 * scriveva "10.0".
 */
class GradedLabTest {

    private fun slab(
        id: String,
        name: String = id,
        grade: Float? = 10f,
        company: String = "PSA",
        value: Double = 0.0,
        quantity: Int = 1,
        setName: String = "Base",
        number: String = "1",
        addedAt: Timestamp? = null
    ) = PokemonCard(
        id = id,
        name = name,
        set = setName,
        cardNumber = number,
        grade = grade,
        gradingCompany = company,
        estimatedValue = value,
        quantity = quantity,
        addedAt = addedAt
    ).also { it.isGraded = true }

    // ── Voto stampato ─────────────────────────────────────────────────────

    @Test
    fun ilDieciNonHaDecimali() {
        assertEquals("10", GradedLab.formatGrade(10f))
    }

    @Test
    fun ilMezzoPuntoResta() {
        assertEquals("9.5", GradedLab.formatGrade(9.5f))
    }

    @Test
    fun senzaVotoSiScriveUnTrattino() {
        assertEquals("—", GradedLab.formatGrade(null))
        assertEquals("—", GradedLab.formatGrade(0f))
    }

    /**
     * La media non e' un voto: arrotondarla a mezzo punto — come fa l'etichetta
     * di un ente — scriverebbe 9.5 dove il conto dice 9.3.
     */
    @Test
    fun laMediaTieneIlSuoDecimale() {
        assertEquals("9.3", GradedLab.formatGrade(9.333f))
        assertEquals("8.7", GradedLab.formatGrade(8.666f))
    }

    @Test
    fun unaMediaCheArrotondaADieciNonSiScriveDieciVirgolaZero() {
        assertEquals("10", GradedLab.formatGrade(9.96f))
    }

    // ── Fasce ─────────────────────────────────────────────────────────────

    @Test
    fun leFasceSeguonoLeSoglieDeiCollezionisti() {
        assertEquals(GradeTier.GEM, GradedLab.tierOf(10f))
        assertEquals(GradeTier.MINT, GradedLab.tierOf(9.5f))
        assertEquals(GradeTier.MINT, GradedLab.tierOf(9f))
        assertEquals(GradeTier.NEAR_MINT, GradedLab.tierOf(8.5f))
        assertEquals(GradeTier.EXCELLENT, GradedLab.tierOf(7f))
        assertEquals(GradeTier.EXCELLENT, GradedLab.tierOf(6f))
        assertEquals(GradeTier.PLAYED, GradedLab.tierOf(5.5f))
        assertEquals(GradeTier.UNGRADED, GradedLab.tierOf(null))
    }

    @Test
    fun laDistribuzioneSaltaLeFasceVuoteETieneLOrdineDelleFasce() {
        val buckets = GradedLab.tierCounts(
            listOf(
                slab("a", grade = 7f),
                slab("b", grade = 10f),
                slab("c", grade = 10f)
            )
        )

        assertEquals(listOf(GradeTier.GEM, GradeTier.EXCELLENT), buckets.map { it.tier })
        assertEquals(listOf(2, 1), buckets.map { it.count })
    }

    // ── Enti ──────────────────────────────────────────────────────────────

    @Test
    fun lEnteScrittoInMinuscoloNonDiventaUnSecondoFiltro() {
        val companies = GradedLab.companyCounts(
            listOf(slab("a", company = "psa"), slab("b", company = "PSA"))
        )

        assertEquals(1, companies.size)
        assertEquals("PSA", companies.first().key)
        assertEquals(2, companies.first().count)
    }

    /**
     * La regressione che ha motivato [GradedLab.UNKNOWN_COMPANY]: il chip si
     * costruiva su "N/D" ma il filtro confrontava la stringa vuota, quindi
     * selezionarlo svuotava la griglia.
     */
    @Test
    fun ilFiltroSenzaEntePescaLeCarteSenzaEnte() {
        val cards = listOf(slab("a", company = ""), slab("b", company = "BGS"))
        val companies = GradedLab.companyCounts(cards)
        val unknown = companies.first { it.key == GradedLab.UNKNOWN_COMPANY }

        assertEquals(1, unknown.count)
        assertEquals(
            listOf("a"),
            GradedLab.filter(cards, company = GradedLab.UNKNOWN_COMPANY).map { it.id }
        )
    }

    @Test
    fun lOrdineDeiChipEStabileAPariConto() {
        val cards = listOf(
            slab("a", company = "CGC"),
            slab("b", company = "BGS"),
            slab("c", company = "PSA"),
            slab("d", company = "PSA")
        )

        assertEquals(
            listOf("PSA", "BGS", "CGC"),
            GradedLab.companyCounts(cards).map { it.key }
        )
        // Le stesse carte in un altro ordine danno la stessa fila.
        assertEquals(
            GradedLab.companyCounts(cards).map { it.key },
            GradedLab.companyCounts(cards.reversed()).map { it.key }
        )
    }

    // ── Riassunto ─────────────────────────────────────────────────────────

    @Test
    fun dueCopieDellaStessaSlabContanoDueVolte() {
        val summary = GradedLab.summary(listOf(slab("a", grade = 9f, value = 100.0, quantity = 2)))

        assertEquals(2, summary.slabs)
        assertEquals(1, summary.entries)
        assertEquals(200.0, summary.totalValue, 0.001)
    }

    @Test
    fun laMediaEPesataSullaQuantita() {
        val summary = GradedLab.summary(
            listOf(
                slab("a", grade = 10f, quantity = 3),
                slab("b", grade = 6f, quantity = 1)
            )
        )

        assertEquals(4, summary.slabs)
        assertEquals(9f, summary.averageGrade, 0.001f)
        assertEquals(3, summary.gems)
    }

    @Test
    fun unaSlabSenzaVotoNonAbbassaLaMedia() {
        val summary = GradedLab.summary(
            listOf(slab("a", grade = 9f), slab("b", grade = null))
        )

        assertEquals(9f, summary.averageGrade, 0.001f)
        assertEquals(1, summary.graded)
        assertEquals(1, summary.ungraded)
    }

    @Test
    fun senzaNessunVotoLaMediaEZeroENonUnErrore() {
        val summary = GradedLab.summary(listOf(slab("a", grade = null)))

        assertEquals(0f, summary.averageGrade, 0.001f)
        assertEquals(0, summary.graded)
    }

    /**
     * Il totale in cima e' una stima al ribasso finche' qualche slab non ha un
     * valore: la schermata deve poterlo dire, quindi il conto serve.
     */
    @Test
    fun leSlabSenzaPrezzoSonoContateAParte() {
        val summary = GradedLab.summary(
            listOf(slab("a", value = 50.0), slab("b", value = 0.0, quantity = 2))
        )

        assertEquals(3, summary.slabs)
        assertEquals(1, summary.pricedSlabs)
        assertEquals(2, summary.unpricedSlabs)
        assertEquals(50.0, summary.totalValue, 0.001)
    }

    @Test
    fun unaCollezioneVuotaNonDividePerZero() {
        val summary = GradedLab.summary(emptyList())

        assertEquals(0, summary.slabs)
        assertEquals(0f, summary.averageGrade, 0.001f)
        assertEquals(0.0, summary.totalValue, 0.001)
        assertEquals(0, summary.unpricedSlabs)
    }

    // ── Ricerca e filtri ──────────────────────────────────────────────────

    @Test
    fun laRicercaGuardaNomeEspansioneNumeroEdEnte() {
        val cards = listOf(
            slab("a", name = "Charizard", setName = "Base", number = "4", company = "PSA"),
            slab("b", name = "Blastoise", setName = "Jungle", number = "9", company = "BGS")
        )

        assertEquals(listOf("a"), GradedLab.filter(cards, query = "chari").map { it.id })
        assertEquals(listOf("b"), GradedLab.filter(cards, query = "jungle").map { it.id })
        assertEquals(listOf("b"), GradedLab.filter(cards, query = "BGS").map { it.id })
        assertEquals(listOf("a"), GradedLab.filter(cards, query = "4").map { it.id })
    }

    @Test
    fun unaRicercaDiSoliSpaziNonFiltraNiente() {
        val cards = listOf(slab("a"), slab("b"))
        assertEquals(2, GradedLab.filter(cards, query = "   ").size)
    }

    @Test
    fun iFiltriSiSommano() {
        val cards = listOf(
            slab("a", grade = 10f, company = "PSA"),
            slab("b", grade = 9f, company = "PSA"),
            slab("c", grade = 10f, company = "BGS")
        )

        assertEquals(
            listOf("a"),
            GradedLab.filter(cards, company = "PSA", tier = GradeTier.GEM).map { it.id }
        )
    }

    // ── Ordinamento ───────────────────────────────────────────────────────

    @Test
    fun perVotoLeSlabSenzaVotoFinisconoInFondo() {
        val cards = listOf(
            slab("senzavoto", grade = null),
            slab("nove", grade = 9f),
            slab("dieci", grade = 10f)
        )

        assertEquals(
            listOf("dieci", "nove", "senzavoto"),
            GradedLab.sort(cards, GradedSort.GRADE_DESC).map { it.id }
        )
    }

    @Test
    fun aPariVotoDecideIlValore() {
        val cards = listOf(
            slab("economica", grade = 10f, value = 10.0),
            slab("cara", grade = 10f, value = 900.0)
        )

        assertEquals(
            listOf("cara", "economica"),
            GradedLab.sort(cards, GradedSort.GRADE_DESC).map { it.id }
        )
    }

    @Test
    fun perValoreContaLaQuantita() {
        val cards = listOf(
            slab("una", value = 100.0, quantity = 1),
            slab("tre", value = 50.0, quantity = 3)
        )

        assertEquals(
            listOf("tre", "una"),
            GradedLab.sort(cards, GradedSort.VALUE_DESC).map { it.id }
        )
    }

    @Test
    fun perNomeLOrdineIgnoraLeMaiuscole() {
        val cards = listOf(slab("b", name = "zapdos"), slab("a", name = "Arcanine"))

        assertEquals(
            listOf("a", "b"),
            GradedLab.sort(cards, GradedSort.NAME).map { it.id }
        )
    }

    @Test
    fun perRecentiPrimaLeUltimeArrivateEPerUltimeQuelleSenzaData() {
        val cards = listOf(
            slab("vecchia", addedAt = Timestamp(1_000, 0)),
            slab("senzadata", addedAt = null),
            slab("nuova", addedAt = Timestamp(2_000, 0))
        )

        assertEquals(
            listOf("nuova", "vecchia", "senzadata"),
            GradedLab.sort(cards, GradedSort.RECENT).map { it.id }
        )
    }

    @Test
    fun lOrdinamentoNonPerdeNeDuplicaCarte() {
        val cards = (1..10).map { slab("c$it", grade = (it % 5) + 5f, value = it.toDouble()) }

        GradedSort.entries.forEach { sort ->
            val sorted = GradedLab.sort(cards, sort)
            assertEquals(cards.size, sorted.size)
            assertEquals(cards.map { it.id }.toSet(), sorted.map { it.id }.toSet())
        }
    }

    // ── Griglia ───────────────────────────────────────────────────────────

    @Test
    fun visibleFiltraEOrdinaInUnPassaggio() {
        val cards = listOf(
            slab("a", name = "Charizard", grade = 9f, company = "PSA", value = 10.0),
            slab("b", name = "Charizard", grade = 10f, company = "PSA", value = 20.0),
            slab("c", name = "Pikachu", grade = 10f, company = "BGS")
        )

        val visible = GradedLab.visible(cards, query = "chari", company = "PSA")

        assertEquals(listOf("b", "a"), visible.map { it.id })
    }

    @Test
    fun unFiltroCheNonPescaNienteRestituisceUnaListaVuotaENonTutto() {
        val cards = listOf(slab("a", company = "PSA"))
        val visible = GradedLab.visible(cards, company = "CGC")

        assertTrue(visible.isEmpty())
    }

    @Test
    fun ilValoreDiUnaRigaSenzaPrezzoEZero() {
        assertEquals(0.0, GradedLab.slabValue(slab("a", value = 0.0, quantity = 4)), 0.001)
    }

    @Test
    fun unaQuantitaAZeroValeComunqueUnPezzo() {
        // Firestore puo' restituire 0 se il campo manca: una slab esiste, e va
        // contata una volta, non zero.
        val summary = GradedLab.summary(listOf(slab("a", grade = 9f, value = 30.0, quantity = 0)))

        assertEquals(1, summary.slabs)
        assertEquals(30.0, summary.totalValue, 0.001)
        assertEquals(9f, summary.averageGrade, 0.001f)
    }

    @Test
    fun nessunaFasciaPerUnaListaVuota() {
        assertTrue(GradedLab.tierCounts(emptyList()).isEmpty())
        assertTrue(GradedLab.companyCounts(emptyList()).isEmpty())
        assertNull(GradedLab.tierCounts(emptyList()).firstOrNull())
    }
}
