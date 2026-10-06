package com.emabuia.pokevault.util

import com.emabuia.pokevault.data.formatAmount
import com.emabuia.pokevault.data.model.Timestamp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * SimpleDateFormat("dd/MM/yyyy") di Android: il giorno nel fuso del telefono.
 * [zone] c'e' per i test.
 */
fun formatDayMonthYear(timestamp: Timestamp, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val date = Instant.fromEpochSeconds(timestamp.seconds, timestamp.nanoseconds).toLocalDateTime(zone).date
    return "${date.day.toString().padStart(2, '0')}/${date.month.number.toString().padStart(2, '0')}/${date.year}"
}

/** SimpleDateFormat("dd/MM HH:mm"): quando e' stata salvata una mano. */
fun formatDayMonthTime(millis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val time = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone)
    fun two(n: Int) = n.toString().padStart(2, '0')
    return "${two(time.day)}/${two(time.month.number)} ${two(time.hour)}:${two(time.minute)}"
}

// I mesi come li scrive Java con Locale.ITALIAN (MMMM e MMM).
private val ITALIAN_MONTHS = listOf(
    "gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno",
    "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre",
)
private val ITALIAN_MONTHS_SHORT = listOf("gen", "feb", "mar", "apr", "mag", "giu", "lug", "ago", "set", "ott", "nov", "dic")

/** DateTimeFormatter "d MMMM" in italiano: "6 ottobre". */
fun formatDayMonthName(millis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val date = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone).date
    return "${date.day} ${ITALIAN_MONTHS[date.month.number - 1]}"
}

/** DateTimeFormatter "d MMM yyyy" in italiano: "6 ott 2026". */
fun formatDayShortMonthYear(millis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String {
    val date = Instant.fromEpochMilliseconds(millis).toLocalDateTime(zone).date
    return "${date.day} ${ITALIAN_MONTHS_SHORT[date.month.number - 1]} ${date.year}"
}

/** "%.2f".format(value) su un telefono in italiano: due decimali, con la virgola. */
fun formatEuro(value: Double): String = formatAmount(value)

/** "%.1f".format(value) su un telefono in italiano: un decimale, con la virgola. */
fun formatOneDecimal(value: Double): String {
    // Il mezzo si arrotonda in su, come Java (12,25 -> 12,3): kotlin.math.round
    // andrebbe al pari e darebbe 12,2.
    val abs = kotlin.math.floor(kotlin.math.abs(value) * 10 + 0.5).toLong()
    val sign = if (value < 0 && abs > 0) "-" else ""
    return "$sign${abs / 10},${abs % 10}"
}
