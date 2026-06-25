package com.huanfuli.lapsight.shared

/**
 * Android/JVM actual for [nowEpochMillis]. Host unit tests compile against this
 * actual (the shared module's host test target is the Android library).
 */
actual fun nowEpochMillis(): Long = System.currentTimeMillis()
