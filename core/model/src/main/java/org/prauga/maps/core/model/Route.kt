/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.core.model

/** How the route is travelled. */
enum class TravelMode {
    Car,
    Bike,
    Walk,
}

/** A way from one place to another, with the turns along it. */
data class Route(
    val mode: TravelMode,
    /** The line to draw, start to end. */
    val shape: List<Coordinates>,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val maneuvers: List<Maneuver>,
)

/** One instruction along a [Route], such as "Turn left onto 35th Cross Road". */
data class Maneuver(
    val instruction: String,
    /** Spoken form, phrased for text-to-speech, when the engine gives one. */
    val verbalInstruction: String?,
    val distanceMeters: Double,
    val durationSeconds: Double,
    /** Index into [Route.shape] where this maneuver starts. */
    val shapeIndex: Int,
    val kind: ManeuverKind,
)

/** What a [Maneuver] asks the user to do, for its arrow. */
enum class ManeuverKind {
    Depart,
    Straight,
    SlightLeft,
    SlightRight,
    Left,
    Right,
    SharpLeft,
    SharpRight,
    UTurn,
    RampLeft,
    RampRight,
    Merge,
    Roundabout,
    Arrive,
}
