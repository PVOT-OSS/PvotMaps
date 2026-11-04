package org.prauga.maps.feature.map

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.prauga.pvot.designsystem.components.PvotPersistentSheetDefaults
import com.prauga.pvot.designsystem.components.PvotSheetDetent
import com.prauga.pvot.designsystem.components.rememberPvotPersistentSheetState
import com.prauga.pvot.designsystem.effects.pvotGlassBackdrop
import com.prauga.pvot.designsystem.effects.rememberPvotGlassBackdrop
import com.prauga.pvot.designsystem.theme.PvotSpacing
import kotlin.math.roundToInt
import org.prauga.maps.core.model.MapCamera
import org.prauga.maps.core.model.MapMode
import org.prauga.maps.core.model.MapSettings
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
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            // Wait the moment it takes to read the settings, so the map doesn't load a style twice.
            settings?.let {
                MapWithOverlay(
                    initialCamera = state.camera,
                    userPosition = userPosition,
                    settings = it,
                    onModeChange = viewModel::setMode,
                    onLabelsChange = viewModel::setLabels,
                    onTiltedChange = viewModel::setTilted,
                )
            }
        }
        MapUiState.Locating ->
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        MapUiState.LocationUnavailable -> LocationUnavailable(onRetry = viewModel::locate)
    }
}

private const val TiltDegrees = 60.0

@Composable
private fun MapWithOverlay(
    initialCamera: MapCamera,
    userPosition: UserPosition?,
    settings: MapSettings,
    onModeChange: (MapMode) -> Unit,
    onLabelsChange: (Boolean) -> Unit,
    onTiltedChange: (Boolean) -> Unit,
) {
    val mapState = rememberMapState(initialCamera)
    val sheetState = rememberPvotPersistentSheetState()
    val backdrop = rememberPvotGlassBackdrop()
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var toolbarHeight by remember { mutableIntStateOf(0) }
    var choosingMode by rememberSaveable { mutableStateOf(false) }
    StatusBarIcons(overSpace = mapState.showsSpace)
    // Each time the map is created it starts flat; bring back the tilt the user left it at.
    LaunchedEffect(mapState) { if (settings.tilted) mapState.animateTilt(TiltDegrees) }
    Box(Modifier.fillMaxSize()) {
        MapSurface(
            state = mapState,
            userPosition = userPosition,
            mode = settings.mode,
            labels = settings.labels,
            modifier = Modifier.fillMaxSize().pvotGlassBackdrop(backdrop),
            contentPadding =
                PaddingValues(
                    bottom =
                        SearchPeekHeight +
                            PvotPersistentSheetDefaults.FloatingMargin +
                            navigationBarBottom
                ),
        )
        Box(Modifier.fillMaxSize().statusBarsPadding()) {
            // The pill rides just above the sheet and steps aside when the sheet fills the screen.
            AnimatedVisibility(
                visible =
                    !sheetState.offset.isNaN() &&
                        sheetState.targetDetent != PvotSheetDetent.Expanded,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier =
                    Modifier.align(Alignment.TopEnd)
                        .offset {
                            val y = sheetState.offset - toolbarHeight - PvotSpacing.M.toPx()
                            IntOffset(0, if (y.isNaN()) 0 else y.roundToInt())
                        }
                        .padding(end = PvotSpacing.L)
                        .onSizeChanged { toolbarHeight = it.height },
            ) {
                MapToolbar(
                    backdrop = backdrop,
                    onMapModes = { choosingMode = true },
                    tilted = settings.tilted,
                    onTilt = {
                        val tilted = !settings.tilted
                        onTiltedChange(tilted)
                        mapState.animateTilt(if (tilted) TiltDegrees else 0.0)
                    },
                    onMyLocation = {
                        val target = userPosition?.coordinates ?: return@MapToolbar
                        mapState.animateTo(
                            MapCamera(target, maxOf(mapState.camera.zoom, StreetZoom))
                        )
                    },
                )
            }
            MapSearchSheet(state = sheetState, backdrop = backdrop)
        }
    }
    MapModesSheet(
        visible = choosingMode,
        mode = settings.mode,
        onModeChange = onModeChange,
        labels = settings.labels,
        onLabelsChange = onLabelsChange,
        onDismissRequest = { choosingMode = false },
    )
}

// Space around the globe is black in both themes, so over it the status bar needs light icons.
@Composable
private fun StatusBarIcons(overSpace: Boolean) {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    val dark = isSystemInDarkTheme()
    DisposableEffect(window, view, overSpace, dark) {
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !dark && !overSpace
        onDispose { controller.isAppearanceLightStatusBars = !dark }
    }
}

@Composable
private fun LocationUnavailable(onRetry: () -> Unit) {
    Column(
        modifier =
            Modifier.fillMaxSize()
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
        Button(onClick = onRetry) { Text(stringResource(R.string.location_unavailable_retry)) }
    }
}
