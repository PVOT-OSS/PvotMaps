/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.feature.map

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.prauga.maps.core.location.LocationProvider
import org.prauga.maps.core.model.Coordinates
import org.prauga.maps.core.model.Maneuver
import org.prauga.maps.core.model.Place
import org.prauga.maps.core.model.Route
import org.prauga.maps.core.model.TravelMode
import org.prauga.maps.core.navigation.RouteProgress
import org.prauga.maps.core.navigation.RouteTracker
import org.prauga.maps.core.routing.RouteResult
import org.prauga.maps.core.routing.RoutingEngine

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

/**
 * Guidance for the whole app, not one screen: it follows the location itself and speaks the turns,
 * while [NavigationService] keeps the process in the foreground, so it carries on with the screen
 * off or another app in front.
 */
@Singleton
internal class NavigationSession
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val locationProvider: LocationProvider,
    private val routing: RoutingEngine,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _navigation = MutableStateFlow<Navigation?>(null)
    val navigation: StateFlow<Navigation?> = _navigation.asStateFlow()

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private var tracker: RouteTracker? = null
    private var announced = mutableSetOf<Pair<Int, Boolean>>()
    private var offRouteFixes = 0
    private var locationJob: Job? = null
    private var rerouteJob: Job? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    fun start(to: Place, route: Route) {
        follow(to, route)
        startVoice()
        ContextCompat.startForegroundService(context, NavigationService.intent(context))
        locationJob?.cancel()
        locationJob = scope.launch { locationProvider.locationUpdates().collect(::onPosition) }
        route.maneuvers.firstOrNull()?.let { say(Announcement.Turn(it, null)) }
    }

    fun stop() {
        locationJob?.cancel()
        rerouteJob?.cancel()
        tracker = null
        _navigation.value = null
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    fun setMuted(muted: Boolean) {
        _muted.value = muted
        if (muted) tts?.stop()
    }

    private fun onPosition(position: Coordinates) {
        val navigation = _navigation.value ?: return
        val progress = tracker?.update(position) ?: return
        if (navigation.progress?.arrived != true && progress.arrived) say(Announcement.Arrived)
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
                say(Announcement.Turn(maneuver, null))
            distance in late..early && announced.add(index to false) ->
                say(Announcement.Turn(maneuver, distance))
        }
    }

    private fun reroute(navigation: Navigation, from: Coordinates) {
        _navigation.value = navigation.copy(rerouting = true)
        say(Announcement.Rerouting)
        rerouteJob =
            scope.launch {
                val result = routing.route(from, navigation.to.coordinates, navigation.route.mode)
                if (_navigation.value == null) return@launch
                if (result is RouteResult.Found) {
                    follow(navigation.to, result.route)
                } else {
                    offRouteFixes = 0
                    _navigation.value = _navigation.value?.copy(rerouting = false)
                }
            }
    }

    private fun startVoice() {
        if (tts != null) return
        tts =
            TextToSpeech(context) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) tts?.language = Locale.getDefault()
            }
    }

    // Queued, not flushed: a turn announced right after another must not cut it off.
    private fun say(announcement: Announcement) {
        val voice = tts ?: return
        if (_muted.value || !ttsReady) return
        voice.speak(announcement.text(), TextToSpeech.QUEUE_ADD, null, UtteranceId)
    }

    private fun Announcement.text(): String {
        val resources = context.resources
        return when (this) {
            Announcement.Arrived -> resources.getString(R.string.map_nav_arrived)
            Announcement.Rerouting -> resources.getString(R.string.map_nav_rerouting)
            is Announcement.Turn -> {
                val instruction = maneuver.verbalInstruction ?: maneuver.instruction
                if (distanceMeters == null) {
                    instruction
                } else {
                    resources.getString(
                        R.string.map_nav_in_distance,
                        formatDistance(resources, distanceMeters),
                        instruction,
                    )
                }
            }
        }
    }

    private companion object {
        const val UtteranceId = "pvot-maps"
        const val OffRouteFixes = 2
        const val CarEarly = 500.0
        const val CarLate = 80.0
        const val SlowEarly = 150.0
        const val SlowLate = 30.0
    }
}
