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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.prauga.maps.core.location.HeadingProvider
import org.prauga.maps.core.location.LocationProvider
import org.prauga.maps.core.model.Coordinates
import org.prauga.maps.core.model.MapCamera
import org.prauga.maps.core.model.MapMode
import org.prauga.maps.core.model.MapSettings
import org.prauga.maps.core.model.UserPosition
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
                UserPosition(coordinates, heading)
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

    fun locate() {
        if (_uiState.value is MapUiState.Ready || locateJob?.isActive == true) return
        locateJob =
            viewModelScope.launch {
                _uiState.value = MapUiState.Locating
                val location = locationProvider.currentLocation()
                firstFix = location
                _uiState.value =
                    if (location != null) {
                        MapUiState.Ready(MapCamera(location, StreetZoom))
                    } else {
                        MapUiState.LocationUnavailable
                    }
            }
    }

    private companion object {
        const val StopTimeoutMillis = 5_000L
    }
}
