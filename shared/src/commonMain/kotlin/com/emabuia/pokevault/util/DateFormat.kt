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

/** "%.2f".format(value) su un telefono in italiano: due decimali, con la virgola. */
fun formatEuro(value: Double): String = formatAmount(value)
