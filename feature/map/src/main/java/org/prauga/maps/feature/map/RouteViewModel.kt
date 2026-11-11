/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.prauga.maps.core.model.Coordinates
import org.prauga.maps.core.model.Maneuver
import org.prauga.maps.core.model.Place
import org.prauga.maps.core.model.Route
import org.prauga.maps.core.model.TravelMode
import org.prauga.maps.core.navigation.RouteProgress
import org.prauga.maps.core.navigation.RouteTracker
import org.prauga.maps.core.routing.RouteResult
import org.prauga.maps.core.routing.RoutingEngine

internal enum class RouteFailure {
    /** There is no fix yet, so nowhere to start from. */
    NoLocation,
    NoData,
    NotFound,
}

internal sealed interface RoutePlan {
    data object None : RoutePlan

    data class Planning(val to: Place, val mode: TravelMode) : RoutePlan

    data class Ready(val to: Place, val route: Route) : RoutePlan

    data class Failed(val to: Place, val mode: TravelMode, val failure: RouteFailure) : RoutePlan
}

/** Turn-by-turn guidance along a route, as the user moves. */
internal data class Navigation(
    val to: Place,
    val route: Route,
    val progress: RouteProgress?,
    val rerouting: Boolean = false,
)

/** Something to say aloud: a maneuver, ahead by [distanceMeters] or now when null; or arrival. */
internal sealed interface Announcement {
    data class Turn(val maneuver: Maneuver, val distanceMeters: Double?) : Announcement

    data object Rerouting : Announcement

    data object Arrived : Announcement
}

@HiltViewModel
internal class RouteViewModel @Inject constructor(private val routing: RoutingEngine) :
    ViewModel() {

    private val _plan = MutableStateFlow<RoutePlan>(RoutePlan.None)
    val plan: StateFlow<RoutePlan> = _plan.asStateFlow()

    private var job: Job? = null

    /** Plans a route from [from], where the user is, to [to]; a newer plan replaces this one. */
    fun plan(to: Place, from: Coordinates?, mode: TravelMode) {
        job?.cancel()
        if (from == null) {
            _plan.value = RoutePlan.Failed(to, mode, RouteFailure.NoLocation)
            return
        }
        _plan.value = RoutePlan.Planning(to, mode)
        job =
            viewModelScope.launch {
                _plan.value =
                    when (val result = routing.route(from, to.coordinates, mode)) {
                        is RouteResult.Found -> RoutePlan.Ready(to, result.route)
                        RouteResult.NoData -> RoutePlan.Failed(to, mode, RouteFailure.NoData)
                        is RouteResult.NotFound -> RoutePlan.Failed(to, mode, RouteFailure.NotFound)
                    }
            }
    }

    fun clear() {
        stop()
        job?.cancel()
        _plan.value = RoutePlan.None
    }

    private val _navigation = MutableStateFlow<Navigation?>(null)
    val navigation: StateFlow<Navigation?> = _navigation.asStateFlow()

    private val _announcements = MutableSharedFlow<Announcement>(extraBufferCapacity = 8)
    val announcements: SharedFlow<Announcement> = _announcements.asSharedFlow()

    private var tracker: RouteTracker? = null
    private var announced = mutableSetOf<Pair<Int, Boolean>>()
    private var offRouteFixes = 0
    private var rerouteJob: Job? = null

    /** Starts guidance along the planned route. */
    fun start() {
        val ready = _plan.value as? RoutePlan.Ready ?: return
        follow(ready.to, ready.route)
        ready.route.maneuvers.firstOrNull()?.let {
            _announcements.tryEmit(Announcement.Turn(it, null))
        }
    }

    fun stop() {
        rerouteJob?.cancel()
        tracker = null
        _navigation.value = null
    }

    /** Moves guidance on to [position]; call it with every fix while navigating. */
    fun onPosition(position: Coordinates) {
        val navigation = _navigation.value ?: return
        val progress = tracker?.update(position) ?: return
        if (navigation.progress?.arrived != true && progress.arrived) {
            _announcements.tryEmit(Announcement.Arrived)
        }
        _navigation.value = navigation.copy(progress = progress)
        if (progress.arrived) return
        offRouteFixes = if (progress.offRoute) offRouteFixes + 1 else 0
        if (offRouteFixes >= OffRouteFixes && !navigation.rerouting) {
            reroute(navigation, position)
            return
        }
        announce(navigation.route, progress)
    }

    private fun follow(to: Place, route: Route) {
        tracker = RouteTracker(route)
        announced = mutableSetOf()
        offRouteFixes = 0
        _navigation.value = Navigation(to, route, progress = null)
    }

    // Each maneuver is announced twice at most: once while still some way off, and once just
    // before it. The first of a route was already said when guidance started.
    private fun announce(route: Route, progress: RouteProgress) {
        val maneuver = progress.nextManeuver ?: return
        val index = route.maneuvers.indexOf(maneuver)
        val distance = progress.distanceToNextManeuverMeters
        val (early, late) =
            if (route.mode == TravelMode.Car) CarEarly to CarLate else SlowEarly to SlowLate
        when {
            distance <= late && announced.add(index to true) ->
                _announcements.tryEmit(Announcement.Turn(maneuver, null))
            distance in late..early && announced.add(index to false) ->
                _announcements.tryEmit(Announcement.Turn(maneuver, distance))
        }
    }

    private fun reroute(navigation: Navigation, from: Coordinates) {
        _navigation.value = navigation.copy(rerouting = true)
        _announcements.tryEmit(Announcement.Rerouting)
        rerouteJob =
            viewModelScope.launch {
                val result = routing.route(from, navigation.to.coordinates, navigation.route.mode)
                if (_navigation.value == null) return@launch
                if (result is RouteResult.Found) {
                    _plan.value = RoutePlan.Ready(navigation.to, result.route)
                    follow(navigation.to, result.route)
                } else {
                    offRouteFixes = 0
                    _navigation.value = _navigation.value?.copy(rerouting = false)
                }
            }
    }

    private companion object {
        const val OffRouteFixes = 2
        const val CarEarly = 500.0
        const val CarLate = 80.0
        const val SlowEarly = 150.0
        const val SlowLate = 30.0
    }
}

internal val RoutePlan.mode: TravelMode?
    get() =
        when (this) {
            RoutePlan.None -> null
            is RoutePlan.Planning -> mode
            is RoutePlan.Ready -> route.mode
            is RoutePlan.Failed -> mode
        }

internal val RoutePlan.destination: Place?
    get() =
        when (this) {
            RoutePlan.None -> null
            is RoutePlan.Planning -> to
            is RoutePlan.Ready -> to
            is RoutePlan.Failed -> to
        }
