package com.emabuia.pokevault.util

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

/** Gli stessi risultati di Java con Locale.ITALIAN / Locale.ITALY, verificati con la JDK 21. */
class DateFormatTest {
    private val rome = TimeZone.of("Europe/Rome")

    // 06/10/2026 alle 12:00 a Roma.
    private val october6 = 1_791_280_800_000L

    @Test
    fun monthNamesAreItalian() {
        assertEquals("6 ottobre", formatDayMonthName(october6, rome))
        assertEquals("6 ott 2026", formatDayShortMonthYear(october6, rome))
    }

    @Test
    fun oneDecimalRoundsHalfUpLikeJava() {
        assertEquals("2,4", formatOneDecimal(2.35))
        assertEquals("0,1", formatOneDecimal(0.05))
        assertEquals("12,3", formatOneDecimal(12.25))
        assertEquals("0,0", formatOneDecimal(0.0))
        assertEquals("-1,5", formatOneDecimal(-1.5))
    }
}
