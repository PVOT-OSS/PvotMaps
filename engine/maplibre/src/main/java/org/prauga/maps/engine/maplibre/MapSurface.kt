package org.prauga.maps.engine.maplibre

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.prauga.maps.core.model.UserPosition

private val OrnamentMargin = 4.dp

/**
 * The map, drawn as a globe when zoomed out, with the user's position as a dot and heading cone.
 * [contentPadding] is the part of the map covered by overlays; the attribution stays clear of it.
 */
@Composable
fun MapSurface(
    state: MapState,
    modifier: Modifier = Modifier,
    userPosition: UserPosition? = null,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val mapView = remember { createMapView(context) }
    val dark = isSystemInDarkTheme()
    var style by remember(mapView) { mutableStateOf<Style?>(null) }

    MapViewLifecycle(mapView)

    DisposableEffect(mapView, state) {
        mapView.getMapAsync(state::attach)
        onDispose { state.detach() }
    }
    LaunchedEffect(mapView, dark) {
        val palette = if (dark) MapPalette.Dark else MapPalette.Light
        val builder = runCatching { fetchMapStyle(MapStyles.BASE, palette, dark) }
            .map { Style.Builder().fromJson(it) }
            .getOrElse { Style.Builder().fromUri(MapStyles.BASE) }
        mapView.getMapAsync { map ->
            map.setStyle(builder) { loaded ->
                loaded.addUserLocationLayer(density.density)
                style = loaded
            }
        }
    }
    LaunchedEffect(style, userPosition) {
        style?.takeIf { it.isFullyLoaded }?.setUserPosition(userPosition)
    }
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
