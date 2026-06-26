package com.huanfuli.lapsight.shared

import kotlin.time.Clock

/**
 * iOS (Kotlin/Native) actual for [nowEpochMillis] using the Kotlin 2.4 stdlib
 * [Clock]. Wall-clock epoch millis for saved-payload created timestamps/ids.
 */
actual fun nowEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()
