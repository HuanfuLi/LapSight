package com.huanfuli.lapsight.shared.ui

import com.huanfuli.lapsight.shared.review.TraceViewport
import com.huanfuli.lapsight.shared.session.GeoPointDto
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TraceViewMarkerTest {

    private val viewport = TraceViewport.fromLayers(
        layers = listOf(
            listOf(
                GeoPointDto(latitude = 39.0, longitude = -86.0),
                GeoPointDto(latitude = 39.0, longitude = -85.999),
                GeoPointDto(latitude = 39.001, longitude = -85.999),
            ),
        ),
        width = 400.0,
        height = 300.0,
        padding = 0.1,
    )

    @Test
    fun courseMarkerReturnsMarkerForFixInsideViewport() {
        val marker = assertNotNull(
            courseMarker(
                viewport = viewport,
                current = GeoPointDto(latitude = 39.0002, longitude = -85.9998),
                previous = GeoPointDto(latitude = 39.0001, longitude = -85.9999),
            ),
        )

        assertTrue(marker.current.x in 0.0..1.0)
        assertTrue(marker.current.y in 0.0..1.0)
        assertNotNull(marker.previous)
    }

    @Test
    fun courseMarkerHidesFixOutsideViewport() {
        assertNull(
            courseMarker(
                viewport = viewport,
                current = GeoPointDto(latitude = 40.0, longitude = -86.0),
                previous = GeoPointDto(latitude = 39.0001, longitude = -85.9999),
            ),
        )
    }

    @Test
    fun courseMarkerDropsOutOfBoundsHeadingOnly() {
        val marker = assertNotNull(
            courseMarker(
                viewport = viewport,
                current = GeoPointDto(latitude = 39.0002, longitude = -85.9998),
                previous = GeoPointDto(latitude = 40.0, longitude = -86.0),
            ),
        )

        assertNull(marker.previous)
    }
}
