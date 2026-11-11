/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.feature.map

import android.hardware.GeomagneticField
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.prauga.maps.core.location.HeadingProvider
import org.prauga.maps.core.location.LocationProvider
import org.prauga.maps.core.model.Coordinates
import org.prauga.maps.core.model.MapCamera
import org.prauga.maps.core.model.MapMode
import org.prauga.maps.core.model.MapSettings
import org.prauga.maps.core.model.UserPosition
import org.prauga.maps.core.model.distanceTo
import org.prauga.maps.core.settings.MapSettingsRepository

internal const val StreetZoom = 15.0

internal sealed interface MapUiState {
    data object Locating : MapUiState

    data class Ready(val camera: MapCamera) : MapUiState

    data object LocationUnavailable : MapUiState
}

@HiltViewModel
internal class MapViewModel
@Inject
constructor(
    private val locationProvider: LocationProvider,
    headingProvider: HeadingProvider,
    private val settingsRepository: MapSettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<MapUiState>(MapUiState.Locating)
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private var locateJob: Job? = null
    private var firstFix: Coordinates? = null

    /** Live position and heading, only while the map is on screen. */
    val userPosition: StateFlow<UserPosition?> =
        combine(
                flow {
                    firstFix?.let { emit(it) }
                    emitAll(locationProvider.locationUpdates())
                },
                headingProvider.heading().map<Float, Float?> { it }.onStart { emit(null) },
            ) { coordinates, heading ->
                UserPosition(coordinates, heading?.let { trueHeading(it, coordinates) })
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopTimeoutMillis), null)

    /** The map's mode, labels and tilt as last left; null until read from storage. */
    val settings: StateFlow<MapSettings?> =
        settingsRepository.settings.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(StopTimeoutMillis),
            null,
        )

    fun setMode(mode: MapMode) {
        viewModelScope.launch { settingsRepository.setMode(mode) }
    }

    fun setLabels(labels: Boolean) {
        viewModelScope.launch { settingsRepository.setLabels(labels) }
    }

    fun setTilted(tilted: Boolean) {
        viewModelScope.launch { settingsRepository.setTilted(tilted) }
    }

    private val _recenter = MutableSharedFlow<Coordinates>(extraBufferCapacity = 1)

    /** Where to fly the camera once a better fix than the one the map opened on comes in. */
    val recenter: SharedFlow<Coordinates> = _recenter.asSharedFlow()

    // Opens the map at once where the phone last knew it was, rather than waiting for a fresh fix
    // that can take minutes indoors, then moves to the fresh fix if it is somewhere else.
    fun locate() {
        if (_uiState.value is MapUiState.Ready || locateJob?.isActive == true) return
        locateJob =
            viewModelScope.launch {
                _uiState.value = MapUiState.Locating
                if (!locationProvider.isEnabled()) {
                    _uiState.value = MapUiState.LocationUnavailable
                    return@launch
                }
                val last = locationProvider.lastKnownLocation()
                if (last != null) open(last, StreetZoom)
                val current =
                    withTimeoutOrNull(FixTimeoutMillis) { locationProvider.currentLocation() }
                when {
                    current != null && last == null -> open(current, StreetZoom)
                    current != null && last != null ->
                        if (last.distanceTo(current) > RecenterMeters) _recenter.emit(current)
                    last == null -> {
                        open(DefaultCenter, DefaultZoom)
                        userPosition.filterNotNull().first().let { _recenter.emit(it.coordinates) }
                    }
                }
            }
    }

    private fun open(at: Coordinates, zoom: Double) {
        firstFix = at.takeIf { zoom == StreetZoom }
        _uiState.value = MapUiState.Ready(MapCamera(at, zoom))
    }

    private companion object {
        const val StopTimeoutMillis = 5_000L
        const val FixTimeoutMillis = 6_000L
        const val RecenterMeters = 200.0
        const val DefaultZoom = 11.0
        // Bengaluru, the only region with search data so far, when there is no fix at all.
        val DefaultCenter = Coordinates(12.9716, 77.5946)
    }
}

// The compass points to magnetic north; the map's north is true north. Add the local declination
// (about -1.5 degrees in Bengaluru, far more elsewhere) so the cone points the right way.
private fun trueHeading(magnetic: Float, at: Coordinates): Float {
    val field =
        GeomagneticField(
            at.latitude.toFloat(),
            at.longitude.toFloat(),
            0f,
            System.currentTimeMillis(),
        )
    return (magnetic + field.declination + 360f) % 360f
}
