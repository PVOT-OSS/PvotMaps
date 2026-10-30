package org.prauga.maps.engine.maplibre

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.prauga.maps.core.model.MapCamera

@Composable
fun MapSurface(
    camera: MapCamera,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mapView = remember { createMapView(context) }
    val styleUri = if (isSystemInDarkTheme()) MapStyles.DARK else MapStyles.LIGHT

    MapViewLifecycle(mapView)

    LaunchedEffect(mapView, styleUri) {
        mapView.getMapAsync { map -> map.setStyle(styleUri) }
    }
    LaunchedEffect(mapView, camera) {
        mapView.getMapAsync { map -> map.cameraPosition = camera.toCameraPosition() }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun createMapView(context: Context): MapView {
    MapLibre.getInstance(context.applicationContext)
    return MapView(context).apply { onCreate(null) }
}

@Composable
private fun MapViewLifecycle(mapView: MapView) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }
}

private fun MapCamera.toCameraPosition(): CameraPosition =
    CameraPosition.Builder()
        .target(LatLng(target.latitude, target.longitude))
        .zoom(zoom)
        .build()
