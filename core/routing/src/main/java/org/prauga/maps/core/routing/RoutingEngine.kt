/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.core.routing

import org.prauga.maps.core.model.Coordinates
import org.prauga.maps.core.model.Route
import org.prauga.maps.core.model.TravelMode

/** Finds routes on the device, from routing data downloaded for a region. */
interface RoutingEngine {
    suspend fun route(from: Coordinates, to: Coordinates, mode: TravelMode): RouteResult
}

sealed interface RouteResult {
    data class Found(val route: Route) : RouteResult

    /** No routing data is installed on the device. */
    data object NoData : RouteResult

    /** The data is there, but no route joins the two points (outside the region, or no road). */
    data class NotFound(val reason: String) : RouteResult
}
