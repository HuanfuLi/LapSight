// Directory: ui/review — package stays `shared.ui` (see ReviewScreen.kt note).
package com.huanfuli.lapsight.shared.ui

import com.huanfuli.lapsight.shared.localUtcOffsetMinutesAt

/**
 * Formats epoch millis as a simple `YYYY-MM-DD HH:MM` label in the user's
 * current system time zone.
 */
internal fun formatEpochMillis(epochMillis: Long): String =
    formatEpochMillisWithUtcOffset(epochMillis, localUtcOffsetMinutesAt(epochMillis))

internal fun formatEpochMillisWithUtcOffset(epochMillis: Long, utcOffsetMinutes: Int): String {
    val secondsTotal = floorDiv(epochMillis, 1000L) + utcOffsetMinutes * 60L
    val daysTotal = floorDiv(secondsTotal, 86_400L)
    val (y, m, d) = gregorianFromEpochDays(daysTotal)
    val secsOfDay = floorMod(secondsTotal, 86_400L)
    val hh = (secsOfDay / 3600).toString().padStart(2, '0')
    val mm = ((secsOfDay % 3600) / 60).toString().padStart(2, '0')
    val dd = d.toString().padStart(2, '0')
    val mm2 = m.toString().padStart(2, '0')
    return "$y-$mm2-$dd $hh:$mm"
}

internal fun formatOneDecimalReview(value: Double): String {
    val scaled = (value * 10.0).toInt()
    return "${scaled / 10}.${scaled % 10}"
}

private fun floorDiv(value: Long, divisor: Long): Long {
    var quotient = value / divisor
    if ((value xor divisor) < 0 && quotient * divisor != value) quotient -= 1
    return quotient
}

private fun floorMod(value: Long, divisor: Long): Long =
    value - floorDiv(value, divisor) * divisor

private fun gregorianFromEpochDays(daysSinceEpoch: Long): Triple<Int, Int, Int> {
    // Howard Hinnant's civil-from-days algorithm (public domain).
    var z = daysSinceEpoch + 719468
    val era = if (z >= 0) z / 146097 else (z - 146096) / 146097
    val doe = (z - era * 146097).toInt()
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    var y = yoe + (era * 400).toInt()
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    if (m <= 2) y += 1
    return Triple(y, m, d)
}
