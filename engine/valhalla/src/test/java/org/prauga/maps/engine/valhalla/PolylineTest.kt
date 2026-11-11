/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.engine.valhalla

import org.junit.Assert.assertEquals
import org.junit.Test

class PolylineTest {
    @Test
    fun decodesSixDigitPolyline() {
        // Google's polyline example points, encoded at Valhalla's precision of six digits.
        val points = decodePolyline6("_izlhA~rlgdF_{geC~ywl@_kwzCn`{nI")
        assertEquals(3, points.size)
        assertEquals(38.5, points[0].latitude, 1e-9)
        assertEquals(-120.2, points[0].longitude, 1e-9)
        assertEquals(40.7, points[1].latitude, 1e-9)
        assertEquals(-120.95, points[1].longitude, 1e-9)
        assertEquals(43.252, points[2].latitude, 1e-9)
        assertEquals(-126.453, points[2].longitude, 1e-9)
    }

    @Test
    fun decodesEmpty() {
        assertEquals(emptyList<Any>(), decodePolyline6(""))
    }
}
