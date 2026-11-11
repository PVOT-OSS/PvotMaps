/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.feature.map

import android.Manifest
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.prauga.maps.core.model.Coordinates
import org.prauga.maps.core.model.MapCamera
import org.prauga.maps.core.model.MapMode
import org.prauga.maps.core.model.MapSettings
import org.prauga.maps.core.model.TravelMode
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
                    recenter = viewModel.recenter,
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
private const val PlaceZoom = 16.5
private val RoutePadding = 48.dp
private const val NavigationZoom = 17.0
private const val NavigationTilt = 45.0
// Share of the map kept free above the user while following, so more of the road ahead shows.
private const val NavigationAhead = 0.4f

@Composable
private fun MapWithOverlay(
    initialCamera: MapCamera,
    recenter: Flow<Coordinates>,
    userPosition: UserPosition?,
    settings: MapSettings,
    onModeChange: (MapMode) -> Unit,
    onLabelsChange: (Boolean) -> Unit,
    onTiltedChange: (Boolean) -> Unit,
    searchViewModel: SearchViewModel = hiltViewModel(),
    routeViewModel: RouteViewModel = hiltViewModel(),
) {
    val mapState = rememberMapState(initialCamera)
    val sheetState = rememberPvotPersistentSheetState()
    val backdrop = rememberPvotGlassBackdrop()
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var toolbarHeight by remember { mutableIntStateOf(0) }
    var choosingMode by rememberSaveable { mutableStateOf(false) }
    var mapHeight by remember { mutableIntStateOf(0) }
    val query by searchViewModel.query.collectAsStateWithLifecycle()
    val results by searchViewModel.results.collectAsStateWithLifecycle()
    val selected by searchViewModel.selected.collectAsStateWithLifecycle()
    val recents by searchViewModel.recents.collectAsStateWithLifecycle()
    val plan by routeViewModel.plan.collectAsStateWithLifecycle()
    val navigation by routeViewModel.navigation.collectAsStateWithLifecycle()
    val muted by routeViewModel.muted.collectAsStateWithLifecycle()
    // Without it the guidance notification is hidden; guidance itself runs either way.
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(navigation?.progress) {
        val progress = navigation?.progress ?: return@LaunchedEffect
        mapState.follow(
            progress.snapped,
            progress.bearing,
            NavigationZoom,
            NavigationTilt,
            topPadding = (mapHeight * NavigationAhead).roundToInt(),
        )
    }
    val density = LocalDensity.current
    val statusBarTop = WindowInsets.statusBars.getTop(density)
    LaunchedEffect(plan) {
        val ready = plan as? RoutePlan.Ready ?: return@LaunchedEffect
        mapState.fitBounds(
            ready.route.shape,
            padding = with(density) { RoutePadding.roundToPx() },
            topPadding = statusBarTop,
            bottomPadding = (mapHeight * PvotPersistentSheetDefaults.MediumFraction).roundToInt(),
        )
    }
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    StatusBarIcons(overSpace = mapState.showsSpace)
    LaunchedEffect(mapState, recenter) {
        recenter.collect { mapState.animateTo(MapCamera(it, StreetZoom)) }
    }
    // Each time the map is created it starts flat; bring back the tilt the user left it at.
    LaunchedEffect(mapState) { if (settings.tilted) mapState.animateTilt(TiltDegrees) }
    Box(Modifier.fillMaxSize().onSizeChanged { mapHeight = it.height }) {
        MapSurface(
            state = mapState,
            userPosition = userPosition,
            selectedPlace = selected?.coordinates,
            route = (plan as? RoutePlan.Ready)?.route?.shape,
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
        navigation?.let { guided ->
            KeepScreenOn()
            NavigationOverlay(
                navigation = guided,
                backdrop = backdrop,
                muted = muted,
                onMutedChange = routeViewModel::setMuted,
                onEnd = {
                    routeViewModel.stop()
                    mapState.fitBounds(
                        guided.route.shape,
                        padding = with(density) { RoutePadding.roundToPx() },
                        topPadding = statusBarTop,
                        bottomPadding =
                            (mapHeight * PvotPersistentSheetDefaults.MediumFraction).roundToInt(),
                    )
                },
            )
        }
        if (navigation == null)
            Box(Modifier.fillMaxSize().statusBarsPadding()) {
                // The pill rides just above the sheet and steps aside when the sheet fills the
                // screen.
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
                MapSearchSheet(
                    state = sheetState,
                    backdrop = backdrop,
                    query = query,
                    // Equally good matches rank by closeness to where the map is looking.
                    onQueryChange = { searchViewModel.onQueryChange(it, mapState.camera.target) },
                    results = results,
                    recents = recents,
                    selected = selected,
                    onPlaceClick = { place ->
                        focusManager.clearFocus()
                        searchViewModel.select(place)
                        scope.launch { sheetState.animateTo(PvotSheetDetent.Medium) }
                        // The half-open sheet covers the bottom of the map; centre the place above
                        // it.
                        mapState.animateTo(
                            MapCamera(place.coordinates, maxOf(mapState.camera.zoom, PlaceZoom)),
                            bottomPadding =
                                (mapHeight * PvotPersistentSheetDefaults.MediumFraction)
                                    .roundToInt(),
                        )
                    },
                    onCloseSelected = {
                        routeViewModel.clear()
                        searchViewModel.clearSelection()
                    },
                    plan = plan,
                    onDirections = {
                        val to = selected ?: return@MapSearchSheet
                        routeViewModel.plan(to, userPosition?.coordinates, TravelMode.Car)
                        scope.launch { sheetState.animateTo(PvotSheetDetent.Medium) }
                    },
                    onModeChange = { mode ->
                        val to = plan.destination ?: return@MapSearchSheet
                        routeViewModel.plan(to, userPosition?.coordinates, mode)
                    },
                    onStartNavigation = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        routeViewModel.start()
                    },
                    onCloseRoute = routeViewModel::clear,
                    userPosition = userPosition?.coordinates,
                )
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
