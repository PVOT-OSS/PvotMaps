/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.feature.map

import android.content.res.Resources
import android.speech.tts.TextToSpeech
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
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
import java.util.Locale
import kotlinx.coroutines.flow.Flow
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

/** Says [announcements] aloud with the phone's own text-to-speech, unless [muted]. */
@Composable
internal fun SpeakAnnouncements(announcements: Flow<Announcement>, muted: Boolean) {
    val context = LocalContext.current
    var ready by remember { mutableStateOf(false) }
    val tts = remember {
        TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
        }
    }
    DisposableEffect(tts) { onDispose { tts.shutdown() } }
    LaunchedEffect(tts, ready) { if (ready) tts.language = Locale.getDefault() }
    val resources = LocalResources.current
    LaunchedEffect(muted) { if (muted) tts.stop() }
    // Queued, not flushed: a turn announced right after another must not cut it off.
    LaunchedEffect(announcements, muted, ready) {
        announcements.collect { announcement ->
            if (!muted && ready) {
                tts.speak(announcement.text(resources), TextToSpeech.QUEUE_ADD, null, "pvot-maps")
            }
        }
    }
}

private fun Announcement.text(resources: Resources): String =
    when (this) {
        Announcement.Arrived -> resources.getString(R.string.map_nav_arrived)
        Announcement.Rerouting -> resources.getString(R.string.map_nav_rerouting)
        is Announcement.Turn -> {
            val instruction = maneuver.verbalInstruction ?: maneuver.instruction
            if (distanceMeters == null) {
                instruction
            } else {
                resources.getString(
                    R.string.map_nav_in_distance,
                    formatDistance(resources, distanceMeters),
                    instruction,
                )
            }
        }
    }

/** Whether the voice is off, kept across rotation. */
@Composable internal fun rememberMuted() = rememberSaveable { mutableStateOf(false) }
