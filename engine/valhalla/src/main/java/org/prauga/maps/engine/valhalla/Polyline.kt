/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.engine.valhalla

import org.prauga.maps.core.model.Coordinates

private const val Precision = 1e6

/** Decodes an encoded polyline at Valhalla's precision of six decimal places. */
internal fun decodePolyline6(encoded: String): List<Coordinates> {
    val points = mutableListOf<Coordinates>()
    var index = 0
    var lat = 0L
    var lon = 0L
    while (index < encoded.length) {
        val (dLat, afterLat) = readValue(encoded, index)
        val (dLon, afterLon) = readValue(encoded, afterLat)
        lat += dLat
        lon += dLon
        index = afterLon
        points += Coordinates(lat / Precision, lon / Precision)
    }
    return points
}

private fun readValue(encoded: String, start: Int): Pair<Long, Int> {
    var result = 0L
    var shift = 0
    var index = start
    do {
        val b = encoded[index++].code - 63
        result = result or ((b and 0x1f).toLong() shl shift)
        shift += 5
    } while (b >= 0x20)
    val value = if (result and 1L != 0L) (result shr 1).inv() else result shr 1
    return value to index
}
