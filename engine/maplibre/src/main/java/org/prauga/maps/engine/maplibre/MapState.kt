package org.prauga.maps.engine.maplibre

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.prauga.maps.core.model.Coordinates
import org.prauga.maps.core.model.MapCamera

private const val AnimationMillis = 600

/** Moves the camera of a [MapSurface] and remembers where it was across recreation. */
@Stable
class MapState internal constructor(private var savedCamera: MapCamera) {
    private var map: MapLibreMap? = null
    private var pendingTilt: Double? = null

    /** Where the map is looking now. */
    val camera: MapCamera
        get() = map?.cameraPosition?.toMapCamera() ?: savedCamera

    fun animateTo(camera: MapCamera) {
        val map = map
        if (map == null) {
            savedCamera = camera
            return
        }
        map.animateCamera(CameraUpdateFactory.newCameraPosition(camera.toCameraPosition()), AnimationMillis)
    }

    /** Tilts the map to [degrees] from straight down, keeping where it looks. */
    fun animateTilt(degrees: Double) {
        val map = map
        if (map == null) {
            pendingTilt = degrees
            return
        }
        val position = CameraPosition.Builder(map.cameraPosition).tilt(degrees).build()
        map.animateCamera(CameraUpdateFactory.newCameraPosition(position), AnimationMillis)
    }

    internal fun attach(map: MapLibreMap) {
        this.map = map
        val position = savedCamera.toCameraPosition()
        map.cameraPosition = pendingTilt?.let { CameraPosition.Builder(position).tilt(it).build() } ?: position
        pendingTilt = null
    }

    internal fun detach() {
        savedCamera = camera
        map = null
    }

    internal companion object {
        val Saver = Saver<MapState, DoubleArray>(
            save = { it.camera.run { doubleArrayOf(target.latitude, target.longitude, zoom) } },
            restore = { MapState(MapCamera(Coordinates(it[0], it[1]), it[2])) },
        )
    }
}

@Composable
fun rememberMapState(initialCamera: MapCamera): MapState =
    rememberSaveable(saver = MapState.Saver) { MapState(initialCamera) }

// Keeps the user's tilt and rotation; only the target and zoom are ours to set.
private fun MapCamera.toCameraPosition(): CameraPosition =
    CameraPosition.Builder()
        .target(LatLng(target.latitude, target.longitude))
        .zoom(zoom)
        .build()

private fun CameraPosition.toMapCamera(): MapCamera {
    val target = target ?: LatLng()
    return MapCamera(Coordinates(target.latitude, target.longitude), zoom)
}
