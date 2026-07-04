// Directory: ui/review — package stays `shared.ui` (see ReviewScreen.kt note).
package com.huanfuli.lapsight.shared.ui

/**
 * Formats epoch millis as a simple `YYYY-MM-DD HH:MM` UTC label without
 * platform date dependencies (Kotlin Multiplatform common code).
 */
internal fun formatEpochMillis(epochMillis: Long): String {
    val secondsTotal = epochMillis / 1000
    // Minimal UTC breakdown; sufficient for review display without java.time.
    val daysTotal = secondsTotal / 86_400
    val (y, m, d) = gregorianFromEpochDays(daysTotal)
    val secsOfDay = secondsTotal % 86_400
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
