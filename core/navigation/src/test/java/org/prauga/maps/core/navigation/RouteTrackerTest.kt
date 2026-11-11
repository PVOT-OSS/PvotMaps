/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.prauga.maps.core.model.Coordinates
import org.prauga.maps.core.model.Maneuver
import org.prauga.maps.core.model.ManeuverKind
import org.prauga.maps.core.model.Route
import org.prauga.maps.core.model.TravelMode

// An L-shaped route in Bengaluru: about 1.1 km north, then about 1.1 km east.
class RouteTrackerTest {
    private val start = Coordinates(12.90, 77.56)
    private val corner = Coordinates(12.91, 77.56)
    private val end = Coordinates(12.91, 77.57)
    private val route =
        Route(
            mode = TravelMode.Car,
            shape = listOf(start, corner, end),
            distanceMeters = 2200.0,
            durationSeconds = 220.0,
            maneuvers =
                listOf(
                    Maneuver("Drive north.", null, 1100.0, 110.0, 0, ManeuverKind.Depart),
                    Maneuver("Turn right.", null, 1100.0, 110.0, 1, ManeuverKind.Right),
                    Maneuver("You have arrived.", null, 0.0, 0.0, 2, ManeuverKind.Arrive),
                ),
        )

    @Test
    fun atTheStartTheTurnIsNext() {
        val progress = RouteTracker(route).update(start)
        assertEquals("Turn right.", progress.nextManeuver?.instruction)
        assertEquals(1113.0, progress.distanceToNextManeuverMeters, 5.0)
        assertFalse(progress.offRoute)
        assertFalse(progress.arrived)
        assertEquals(0.0, progress.bearing, 1.0)
    }

    @Test
    fun halfwayUpTheFirstLegSnapsOntoIt() {
        val progress = RouteTracker(route).update(Coordinates(12.905, 77.5601))
        assertEquals(11.0, progress.distanceFromRoute, 2.0)
        assertEquals(556.0, progress.distanceToNextManeuverMeters, 10.0)
        assertEquals(1650.0, progress.remainingDistanceMeters, 15.0)
    }

    @Test
    fun pastTheCornerOnlyArrivalIsLeft() {
        val tracker = RouteTracker(route)
        tracker.update(start)
        tracker.update(corner)
        val progress = tracker.update(Coordinates(12.91, 77.565))
        assertEquals("You have arrived.", progress.nextManeuver?.instruction)
        assertEquals(542.0, progress.distanceToNextManeuverMeters, 10.0)
        assertEquals(90.0, progress.bearing, 1.0)
    }

    @Test
    fun farFromTheRouteIsOffRoute() {
        assertTrue(RouteTracker(route).update(Coordinates(12.905, 77.565)).offRoute)
    }

    @Test
    fun atTheEndItHasArrived() {
        val tracker = RouteTracker(route)
        tracker.update(corner)
        val progress = tracker.update(Coordinates(12.91, 77.5699))
        assertTrue(progress.arrived)
        assertNull(progress.nextManeuver)
    }
}
