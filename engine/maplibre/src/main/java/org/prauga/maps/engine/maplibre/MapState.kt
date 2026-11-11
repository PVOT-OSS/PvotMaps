/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.engine.maplibre

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.prauga.maps.core.model.Coordinates
import org.prauga.maps.core.model.MapCamera

private const val AnimationMillis = 600
private const val TileSize = 512.0

/** Moves the camera of a [MapSurface] and remembers where it was across recreation. */
@Stable
class MapState internal constructor(private var savedCamera: MapCamera) {
    private var map: MapLibreMap? = null
    private var pendingTilt: Double? = null
    private var viewportHeightDp = 0f
    private val cameraListener = MapLibreMap.OnCameraMoveListener { updateShowsSpace() }

    /**
     * Whether the map is zoomed out so far that space shows above the globe, at the top of the
     * screen.
     */
    var showsSpace: Boolean by mutableStateOf(false)
        private set

    /** Where the map is looking now. */
    val camera: MapCamera
        get() = map?.cameraPosition?.toMapCamera() ?: savedCamera

    /**
     * Flies to [camera]. [bottomPadding] (pixels) is the part of the map covered from below, such
     * as by a sheet, so the target lands in the middle of what is still visible.
     */
    fun animateTo(camera: MapCamera, bottomPadding: Int = 0) {
        val map = map
        if (map == null) {
            savedCamera = camera
            return
        }
        val position =
            CameraPosition.Builder(camera.toCameraPosition())
                .padding(0.0, 0.0, 0.0, bottomPadding.toDouble())
                .build()
        map.animateCamera(CameraUpdateFactory.newCameraPosition(position), AnimationMillis)
    }

    /**
     * Frames all of [points], leaving [padding] pixels free on each side, plus [topPadding] at the
     * top and [bottomPadding] at the bottom for whatever covers the map there (status bar, sheet).
     */
    fun fitBounds(
        points: List<Coordinates>,
        padding: Int,
        topPadding: Int = 0,
        bottomPadding: Int = 0,
    ) {
        val map = map ?: return
        if (points.size < 2) return
        val bounds = LatLngBounds.fromLatLngs(points.map { LatLng(it.latitude, it.longitude) })
        map.animateCamera(
            CameraUpdateFactory.newLatLngBounds(
                bounds,
                padding,
                padding + topPadding,
                padding,
                padding + bottomPadding,
            ),
            AnimationMillis,
        )
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
        map.cameraPosition =
            pendingTilt?.let { CameraPosition.Builder(position).tilt(it).build() } ?: position
        pendingTilt = null
        map.addOnCameraMoveListener(cameraListener)
        updateShowsSpace()
    }

    internal fun detach() {
        savedCamera = camera
        map?.removeOnCameraMoveListener(cameraListener)
        map = null
    }

    internal fun setViewportHeight(heightDp: Float) {
        viewportHeightDp = heightDp
        updateShowsSpace()
    }

    // The globe is drawn at the scale Mercator has at the camera's latitude, so its radius is the
    // world's circumference at this zoom over 2π cos(latitude).
    private fun updateShowsSpace() {
        val position = map?.cameraPosition ?: return
        val latitude = Math.toRadians(position.target?.latitude ?: 0.0)
        val radiusDp = TileSize * 2.0.pow(position.zoom) / (2 * PI * cos(latitude))
        showsSpace = viewportHeightDp > 0f && radiusDp < viewportHeightDp / 2
    }

    internal companion object {
        val Saver =
            Saver<MapState, DoubleArray>(
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
    CameraPosition.Builder().target(LatLng(target.latitude, target.longitude)).zoom(zoom).build()

private fun CameraPosition.toMapCamera(): MapCamera {
    val target = target ?: LatLng()
    return MapCamera(Coordinates(target.latitude, target.longitude), zoom)
}
