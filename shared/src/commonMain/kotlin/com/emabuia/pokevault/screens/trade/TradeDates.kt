package com.emabuia.pokevault.screens.trade

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Le date di TradeRadar, al posto di java.time: giorni e orari degli
 * appuntamenti come li scrive Java con Locale.ITALIAN (verificati con la
 * JDK 21: "dom 4 ott", "ottobre 2026", "LUN").
 *
 * Il server ragiona per testo: giorni "2026-10-04" e orari "17:30", che si
 * confrontano come stringhe. Qui si fa lo stesso.
 */
internal object TradeDates {

    private val WEEKDAYS = listOf("lun", "mar", "mer", "gio", "ven", "sab", "dom")
    private val MONTHS = listOf(
        "gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno",
        "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre",
    )
    private val MONTHS_SHORT = listOf("gen", "feb", "mar", "apr", "mag", "giu", "lug", "ago", "set", "ott", "nov", "dic")

    @OptIn(ExperimentalTime::class)
    private fun now() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

    /** System.currentTimeMillis(). */
    @OptIn(ExperimentalTime::class)
    fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    /** LocalDate.now(). */
    fun today(): LocalDate = now().date

    /** LocalTime.now().toString().take(5): "17:05". */
    fun nowTime(): String {
        val time = now()
        return "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"
    }

    /** I minuti da mezzanotte di "HH:mm" (null se non e' un orario). */
    fun minutesOf(time: String): Int? {
        val hours = time.substringBefore(':').toIntOrNull() ?: return null
        val minutes = time.substringAfter(':', "").toIntOrNull() ?: return null
        return hours * 60 + minutes
    }

    fun parse(day: String?): LocalDate? = day?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun plusDays(date: LocalDate, days: Int): LocalDate = date.plus(DatePeriod(days = days))

    /** "EEE": "sab". */
    fun weekdayShort(date: LocalDate): String = WEEKDAYS[date.dayOfWeek.isoDayNumber - 1]

    /** "MMM": "ott". */
    fun monthShort(date: LocalDate): String = MONTHS_SHORT[date.month.number - 1]

    /** "EEE d MMM": "sab 4 ott". */
    fun dayLabel(date: LocalDate): String = "${weekdayShort(date)} ${date.day} ${monthShort(date)}"

    /** "MMMM yyyy" di un istante: "ottobre 2026". */
    @OptIn(ExperimentalTime::class)
    fun monthYear(millis: Long): String {
        val date = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).date
        return "${MONTHS[date.month.number - 1]} ${date.year}"
    }
}
