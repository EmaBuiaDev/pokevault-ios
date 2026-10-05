package com.emabuia.pokevault.ui.graded

import com.emabuia.pokevault.ui.components.formatEurCompact
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Il campo voto del pannello, come quello del dettaglio collezione su Android. */
class GradingInputTest {

    @Test
    fun theCommaBecomesADotAndLettersDoNotPass() {
        assertEquals("9.5", sanitizeGrade("9,5"))
        assertEquals("9", sanitizeGrade("9a"))
        // Le lettere cadono e le cifre si uniscono: "9a5" e' 95, che si ferma a 10 come su Android.
        assertEquals("10", sanitizeGrade("9a5"))
        // Un punto solo: il secondo cade, le cifre restano.
        assertEquals("9.51", sanitizeGrade("9.5.1"))
    }

    @Test
    fun aHalfTypedGradeIsAccepted() {
        // "9." e' un 9.5 a meta': rifiutarlo vorrebbe dire non poter scrivere 9.5.
        assertEquals("9.", sanitizeGrade("9."))
        assertEquals(9f, parseGrade("9."))
    }

    @Test
    fun aboveTenStopsAtTen() {
        assertEquals("10", sanitizeGrade("11"))
        assertEquals("10", sanitizeGrade("100"))
    }

    @Test
    fun aDotAloneIsNotANumberYet() {
        assertNull(sanitizeGrade("."))
        assertEquals("", sanitizeGrade(""))
    }

    @Test
    fun zeroIsNotAGrade() {
        assertNull(parseGrade("0"))
        assertNull(parseGrade(""))
        assertEquals(10f, parseGrade("10"))
    }

    @Test
    fun compactPricesLikeAndroid() {
        assertEquals("—", formatEurCompact(0.0))
        assertEquals("€ 12,40", formatEurCompact(12.4))
        // Mezzo euro in su, come String.format: 100,50 fa 101, non 100.
        assertEquals("€ 101", formatEurCompact(100.5))
        assertEquals("€ 1234", formatEurCompact(1234.2))
    }
}
