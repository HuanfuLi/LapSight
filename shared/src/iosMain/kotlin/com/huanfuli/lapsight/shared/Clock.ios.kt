package com.huanfuli.lapsight.shared

/**
 * iOS (Kotlin/Native) actual for [nowEpochMillis] using the stdlib monotonic-ish
 * clock. Wall-clock equivalence is not required; saved payloads only need a
 * stable, strictly-increasing created timestamp per save.
 */
actual fun nowEpochMillis(): Long = kotlin.system.getTimeMillis()
