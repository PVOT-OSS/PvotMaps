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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.prauga.maps.core.model.Coordinates
import org.prauga.maps.core.model.Place
import org.prauga.maps.core.model.Route
import org.prauga.maps.core.model.TravelMode
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
        job?.cancel()
        _plan.value = RoutePlan.None
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
