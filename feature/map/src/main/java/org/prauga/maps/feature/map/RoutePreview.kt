/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.feature.map

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import com.prauga.pvot.designsystem.components.PvotSegmentedControl
import com.prauga.pvot.designsystem.theme.PvotSpacing
import kotlin.math.roundToInt
import org.prauga.maps.core.model.Maneuver
import org.prauga.maps.core.model.TravelMode

/** How to get to the chosen place: the travel mode, the trip's time and length, and its turns. */
@Composable
internal fun RoutePreview(
    plan: RoutePlan,
    onModeChange: (TravelMode) -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val to = plan.destination ?: return
    Column(verticalArrangement = Arrangement.spacedBy(PvotSpacing.M)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = stringResource(R.string.map_route_to, to.name),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.map_route_close),
                tint = colors.onSurfaceVariant,
                modifier =
                    Modifier.clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onClose)
                        .padding(PvotSpacing.XS),
            )
        }
        val modes = TravelMode.entries
        PvotSegmentedControl(
            options = modes.map { stringResource(it.label) },
            selectedIndex = modes.indexOf(plan.mode),
            onSelect = { onModeChange(modes[it]) },
            modifier = Modifier.fillMaxWidth(),
        )
        when (plan) {
            RoutePlan.None -> Unit
            is RoutePlan.Planning -> RouteMessage(stringResource(R.string.map_route_planning))
            is RoutePlan.Failed -> RouteMessage(stringResource(plan.failure.message))
            is RoutePlan.Ready -> {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = durationText(plan.route.durationSeconds),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.onSurface,
                    )
                    Text(
                        text = "  " + distanceText(plan.route.distanceMeters),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = PvotSpacing.XS),
                    )
                }
                LazyColumn(
                    contentPadding = WindowInsets.navigationBars.asPaddingValues(),
                    verticalArrangement = Arrangement.spacedBy(PvotSpacing.M),
                ) {
                    itemsIndexed(plan.route.maneuvers) { _, maneuver -> ManeuverRow(maneuver) }
                }
            }
        }
    }
}

@Composable
private fun ManeuverRow(maneuver: Maneuver) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = maneuver.instruction,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (maneuver.distanceMeters > 0) {
            Text(
                text = distanceText(maneuver.distanceMeters),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = PvotSpacing.M),
            )
        }
    }
}

@Composable
private fun RouteMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
internal fun durationText(seconds: Double): String {
    val minutes = (seconds / 60).roundToInt().coerceAtLeast(1)
    return if (minutes < 60) {
        stringResource(R.string.map_duration_min, minutes)
    } else {
        stringResource(R.string.map_duration_h_min, minutes / 60, minutes % 60)
    }
}

private val TravelMode.label: Int
    get() =
        when (this) {
            TravelMode.Car -> R.string.map_route_car
            TravelMode.Bike -> R.string.map_route_bike
            TravelMode.Walk -> R.string.map_route_walk
        }

private val RouteFailure.message: Int
    get() =
        when (this) {
            RouteFailure.NoLocation -> R.string.map_route_no_location
            RouteFailure.NoData -> R.string.map_route_no_data
            RouteFailure.NotFound -> R.string.map_route_not_found
        }
