/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.core.navigation

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.prauga.maps.core.model.Coordinates
import org.prauga.maps.core.model.Maneuver
import org.prauga.maps.core.model.Route
import org.prauga.maps.core.model.TravelMode
import org.prauga.maps.core.model.distanceTo

/** Where the user is along a route, as of the latest position. */
data class RouteProgress(
    /** The position moved onto the route. */
    val snapped: Coordinates,
    /** How far the position is from the route, in metres. */
    val distanceFromRoute: Double,
    val remainingDistanceMeters: Double,
    val remainingDurationSeconds: Double,
    /** The next instruction to follow, or null once only arrival is left. */
    val nextManeuver: Maneuver?,
    val distanceToNextManeuverMeters: Double,
    /** Which way the route runs where the user is, in degrees clockwise from north. */
    val bearing: Double,
    val offRoute: Boolean,
    val arrived: Boolean,
)

/**
 * Follows a user along [route]: each [update] snaps the position onto the route, ahead of where it
 * was last, and works out what is left and what comes next.
 */
class RouteTracker(val route: Route) {
    private val shape = route.shape
    private val cumulative = DoubleArray(shape.size)
    private val total: Double
    private var segment = 0

    init {
        for (i in 1 until shape.size) cumulative[i] =
            cumulative[i - 1] + shape[i - 1].distanceTo(shape[i])
        total = cumulative.lastOrNull() ?: 0.0
    }

    fun update(position: Coordinates): RouteProgress {
        if (shape.size < 2) {
            return progress(shape.firstOrNull() ?: position, 0.0, total, position)
        }
        var best =
            nearest(
                position,
                (segment - BackSegments).coerceAtLeast(0),
                (segment + AheadSegments).coerceAtMost(shape.size - 2),
            )
        // Lost the thread, say after a tunnel or a jump: look along the whole route.
        if (best.distance > offRouteMeters()) {
            val anywhere = nearest(position, 0, shape.size - 2)
            if (anywhere.distance < best.distance) best = anywhere
        }
        segment = best.segment
        val along =
            cumulative[best.segment] +
                best.fraction * (cumulative[best.segment + 1] - cumulative[best.segment])
        return progress(best.point, best.distance, along, position)
    }

    private fun progress(
        snapped: Coordinates,
        distance: Double,
        along: Double,
        position: Coordinates,
    ): RouteProgress {
        val remaining = (total - along).coerceAtLeast(0.0)
        val next =
            route.maneuvers.firstOrNull {
                cumulative[it.shapeIndex.coerceIn(0, shape.lastIndex)] > along + PassedMeters
            }
        val toNext =
            next?.let { cumulative[it.shapeIndex.coerceIn(0, shape.lastIndex)] - along }
                ?: remaining
        return RouteProgress(
            snapped = snapped,
            distanceFromRoute = distance,
            remainingDistanceMeters = remaining,
            remainingDurationSeconds =
                if (total > 0) route.durationSeconds * remaining / total else 0.0,
            nextManeuver =
                next?.takeIf { it !== route.maneuvers.lastOrNull() || remaining > ArrivalMeters },
            distanceToNextManeuverMeters = toNext,
            bearing = bearingAt(segment),
            offRoute = distance > offRouteMeters(),
            arrived = remaining < ArrivalMeters || position.distanceTo(shape.last()) < ArrivalMeters,
        )
    }

    private fun bearingAt(index: Int): Double {
        if (shape.size < 2) return 0.0
        val i = index.coerceIn(0, shape.size - 2)
        val from = shape[i]
        val to = shape[i + 1]
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLon = Math.toRadians(to.longitude - from.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    private fun offRouteMeters() =
        if (route.mode == TravelMode.Car) CarOffRouteMeters else SlowOffRouteMeters

    private data class Nearest(
        val segment: Int,
        val fraction: Double,
        val point: Coordinates,
        val distance: Double,
    )

    // Projects onto each segment in a flat, metre-scaled frame around the position, which is
    // accurate enough over the few hundred metres a segment spans.
    private fun nearest(position: Coordinates, from: Int, to: Int): Nearest {
        val metersPerDegreeLat = 111_320.0
        val metersPerDegreeLon = metersPerDegreeLat * cos(Math.toRadians(position.latitude))
        var best = Nearest(from, 0.0, shape[from], Double.MAX_VALUE)
        for (i in from..to) {
            val a = shape[i]
            val b = shape[i + 1]
            val ax = (a.longitude - position.longitude) * metersPerDegreeLon
            val ay = (a.latitude - position.latitude) * metersPerDegreeLat
            val bx = (b.longitude - position.longitude) * metersPerDegreeLon
            val by = (b.latitude - position.latitude) * metersPerDegreeLat
            val dx = bx - ax
            val dy = by - ay
            val lengthSquared = dx * dx + dy * dy
            val t =
                if (lengthSquared == 0.0) 0.0
                else (-(ax * dx + ay * dy) / lengthSquared).coerceIn(0.0, 1.0)
            val px = ax + t * dx
            val py = ay + t * dy
            val distance = sqrt(px * px + py * py)
            if (distance < best.distance) {
                val point =
                    Coordinates(
                        a.latitude + t * (b.latitude - a.latitude),
                        a.longitude + t * (b.longitude - a.longitude),
                    )
                best = Nearest(i, t, point, distance)
            }
        }
        return best
    }

    private companion object {
        const val BackSegments = 2
        const val AheadSegments = 60
        const val PassedMeters = 5.0
        const val ArrivalMeters = 25.0
        const val CarOffRouteMeters = 50.0
        const val SlowOffRouteMeters = 30.0
    }
}
