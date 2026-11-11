/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.core.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Coordinates(val latitude: Double, val longitude: Double)

private const val EarthRadiusMeters = 6_371_000.0

/** Great-circle distance to [other], in metres. */
fun Coordinates.distanceTo(other: Coordinates): Double {
    val lat1 = Math.toRadians(latitude)
    val lat2 = Math.toRadians(other.latitude)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(other.longitude - longitude)
    val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * EarthRadiusMeters * asin(sqrt(h))
}
