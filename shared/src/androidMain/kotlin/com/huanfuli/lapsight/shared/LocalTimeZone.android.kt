package com.huanfuli.lapsight.shared

import java.util.TimeZone

internal actual fun localUtcOffsetMinutesAt(epochMillis: Long): Int =
    TimeZone.getDefault().getOffset(epochMillis) / 60_000
