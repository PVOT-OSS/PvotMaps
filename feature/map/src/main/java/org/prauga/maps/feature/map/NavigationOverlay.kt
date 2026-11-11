/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.prauga.pvot.designsystem.components.PvotGlassCard
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.button.PvotIconButton
import com.prauga.pvot.designsystem.effects.PvotGlassBackdrop
import com.prauga.pvot.designsystem.theme.PvotSpacing
import java.text.DateFormat
import java.util.Date
import org.prauga.maps.core.model.ManeuverKind

/** The banner with the next turn on top and the trip's progress below, over the map. */
@Composable
internal fun NavigationOverlay(
    navigation: Navigation,
    backdrop: PvotGlassBackdrop,
    muted: Boolean,
    onMutedChange: (Boolean) -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = navigation.progress
    val colors = MaterialTheme.colorScheme
    Box(modifier = modifier.fillMaxSize()) {
        PvotGlassCard(
            backdrop = backdrop,
            modifier =
                Modifier.align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(PvotSpacing.L)
                    .fillMaxWidth(),
            contentPadding = PaddingValues(PvotSpacing.L),
        ) {
            when {
                navigation.rerouting ->
                    Banner(stringResource(R.string.map_nav_rerouting), null, null)
                progress?.arrived == true ->
                    Banner(stringResource(R.string.map_nav_arrived), null, ManeuverKind.Arrive)
                progress?.nextManeuver != null ->
                    Banner(
                        progress.nextManeuver?.instruction.orEmpty(),
                        distanceText(progress.distanceToNextManeuverMeters),
                        progress.nextManeuver?.kind,
                    )
                else -> {
                    val first = navigation.route.maneuvers.firstOrNull()
                    Banner(first?.instruction.orEmpty(), null, first?.kind)
                }
            }
        }
        PvotGlassCard(
            backdrop = backdrop,
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(PvotSpacing.L)
                    .fillMaxWidth(),
            contentPadding = PaddingValues(PvotSpacing.L),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PvotSpacing.M),
            ) {
                val remainingSeconds =
                    progress?.remainingDurationSeconds ?: navigation.route.durationSeconds
                val remainingMeters =
                    progress?.remainingDistanceMeters ?: navigation.route.distanceMeters
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text =
                            stringResource(R.string.map_nav_arrive, arrivalTime(remainingSeconds)),
                        style =
                            MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                        color = colors.onSurface,
                    )
                    Text(
                        text =
                            durationText(remainingSeconds) + " · " + distanceText(remainingMeters),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
                PvotIconButton(
                    icon =
                        painterResource(
                            if (muted) R.drawable.ic_volume_off else R.drawable.ic_volume
                        ),
                    contentDescription =
                        stringResource(
                            if (muted) R.string.map_nav_unmute else R.string.map_nav_mute
                        ),
                    onClick = { onMutedChange(!muted) },
                )
                PvotButton(text = stringResource(R.string.map_nav_end), onClick = onEnd)
            }
        }
    }
}

@Composable
private fun Banner(instruction: String, distance: String?, kind: ManeuverKind?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PvotSpacing.L),
    ) {
        if (kind != null) {
            Icon(
                painter = painterResource(kind.icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(BannerIconSize),
            )
        }
        Column {
            if (distance != null) {
                Text(
                    text = distance,
                    style =
                        MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = instruction,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private val BannerIconSize = 40.dp

private fun arrivalTime(remainingSeconds: Double): String =
    DateFormat.getTimeInstance(DateFormat.SHORT)
        .format(Date(System.currentTimeMillis() + (remainingSeconds * 1000).toLong()))

/** Keeps the screen on while guidance is showing. */
@Composable
internal fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
