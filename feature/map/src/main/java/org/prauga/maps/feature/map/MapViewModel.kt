package org.prauga.maps.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.prauga.maps.core.location.LocationProvider
import org.prauga.maps.core.model.MapCamera
import javax.inject.Inject

private const val StreetZoom = 15.0

internal sealed interface MapUiState {
    data object Locating : MapUiState
    data class Ready(val camera: MapCamera) : MapUiState
    data object LocationUnavailable : MapUiState
}

@HiltViewModel
internal class MapViewModel @Inject constructor(
    private val locationProvider: LocationProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow<MapUiState>(MapUiState.Locating)
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private var locateJob: Job? = null

    fun locate() {
        if (_uiState.value is MapUiState.Ready || locateJob?.isActive == true) return
        locateJob = viewModelScope.launch {
            _uiState.value = MapUiState.Locating
            val location = locationProvider.currentLocation()
            _uiState.value = if (location != null) {
                MapUiState.Ready(MapCamera(location, StreetZoom))
            } else {
                MapUiState.LocationUnavailable
            }
        }
    }
}
