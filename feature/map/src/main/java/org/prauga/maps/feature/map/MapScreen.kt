package org.prauga.maps.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prauga.pvot.designsystem.effects.pvotGlassBackdrop
import com.prauga.pvot.designsystem.effects.rememberPvotGlassBackdrop
import org.prauga.maps.core.model.MapCamera
import org.prauga.maps.core.model.UserPosition
import org.prauga.maps.engine.maplibre.MapSurface
import org.prauga.maps.engine.maplibre.rememberMapState

@Composable
internal fun MapScreen(viewModel: MapViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) { viewModel.locate() }

    when (val state = uiState) {
        is MapUiState.Ready -> {
            val userPosition by viewModel.userPosition.collectAsStateWithLifecycle()
            MapWithOverlay(state.camera, userPosition)
        }
        MapUiState.Locating -> Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        MapUiState.LocationUnavailable -> LocationUnavailable(onRetry = viewModel::locate)
    }
}

@Composable
private fun MapWithOverlay(initialCamera: MapCamera, userPosition: UserPosition?) {
    val mapState = rememberMapState(initialCamera)
    val backdrop = rememberPvotGlassBackdrop()
    val density = LocalDensity.current
    var height by remember { mutableIntStateOf(0) }
    var searchBarTop by remember { mutableFloatStateOf(Float.NaN) }
    var searching by rememberSaveable { mutableStateOf(false) }
    val obstructed = if (searchBarTop.isNaN()) 0f else (height - searchBarTop).coerceAtLeast(0f)
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { height = it.height },
    ) {
        MapSurface(
            state = mapState,
            userPosition = userPosition,
            modifier = Modifier
                .fillMaxSize()
                .pvotGlassBackdrop(backdrop),
            contentPadding = PaddingValues(bottom = with(density) { obstructed.toDp() }),
        )
        // Layers is still a stub.
        MapOverlay(
            backdrop = backdrop,
            onSearch = { searching = true },
            onLayers = {},
            onMyLocation = {
                val target = userPosition?.coordinates ?: return@MapOverlay
                mapState.animateTo(MapCamera(target, maxOf(mapState.camera.zoom, StreetZoom)))
            },
            onSearchBarTop = { searchBarTop = it },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
    MapSearchSheet(visible = searching, onDismissRequest = { searching = false })
}

@Composable
private fun LocationUnavailable(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.location_unavailable_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.location_unavailable_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRetry) {
            Text(stringResource(R.string.location_unavailable_retry))
        }
    }
}
