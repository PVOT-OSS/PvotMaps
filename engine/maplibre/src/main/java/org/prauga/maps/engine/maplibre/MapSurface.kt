package org.prauga.maps.engine.maplibre

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.prauga.maps.core.model.MapCamera

private val OrnamentMargin = 4.dp

@Composable
fun MapSurface(
    camera: MapCamera,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val mapView = remember { createMapView(context) }
    val dark = isSystemInDarkTheme()
    val styleUri = if (dark) MapStyles.DARK else MapStyles.LIGHT

    MapViewLifecycle(mapView)

    LaunchedEffect(mapView, styleUri) {
        val style = runCatching { fetchGlobeStyle(styleUri, dark) }
            .map { Style.Builder().fromJson(it) }
            .getOrElse { Style.Builder().fromUri(styleUri) }
        mapView.getMapAsync { map -> map.setStyle(style) }
    }
    // Keeps the logo and the OpenStreetMap attribution clear of whatever is drawn over the map.
    val ornamentBottom = with(density) { (OrnamentMargin + contentPadding.calculateBottomPadding()).roundToPx() }
    LaunchedEffect(mapView, ornamentBottom) {
        mapView.getMapAsync { map ->
            with(map.uiSettings) {
                setLogoMargins(logoMarginLeft, logoMarginTop, logoMarginRight, ornamentBottom)
                setAttributionMargins(
                    attributionMarginLeft,
                    attributionMarginTop,
                    attributionMarginRight,
                    ornamentBottom,
                )
            }
        }
    }
    LaunchedEffect(mapView, camera) {
        mapView.getMapAsync { map -> map.cameraPosition = camera.toCameraPosition() }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun createMapView(context: Context): MapView {
    MapLibre.getInstance(context.applicationContext)
    // A TextureView draws the map in the view tree, so glass over it can copy and blur it.
    val options = MapLibreMapOptions.createFromAttributes(context).textureMode(true)
    return MapView(context, options).apply { onCreate(null) }
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
