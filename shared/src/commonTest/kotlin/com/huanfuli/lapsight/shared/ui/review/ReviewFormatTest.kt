package com.huanfuli.lapsight.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class ReviewFormatTest {
    @Test
    fun formatsUtcWhenOffsetIsZero() {
        assertEquals(
            "2024-01-01 00:00",
            formatEpochMillisWithUtcOffset(1_704_067_200_000L, 0),
        )
    }

    @Test
    fun appliesPositiveAndNegativeUtcOffsets() {
        assertEquals(
            "2024-01-01 05:30",
            formatEpochMillisWithUtcOffset(1_704_067_200_000L, 330),
        )
        assertEquals(
            "2023-12-31 19:00",
            formatEpochMillisWithUtcOffset(1_704_067_200_000L, -300),
        )
    }
}
