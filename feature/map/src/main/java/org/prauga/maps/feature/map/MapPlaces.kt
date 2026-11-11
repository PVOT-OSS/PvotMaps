/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.feature.map

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.maps.core.model.Place
import org.prauga.maps.core.search.SavedSlot

private const val CircleFillAlpha = 0.08f

/**
 * Shortcuts to the user's own places. A saved one opens its place; an empty one, and Add, start a
 * search, from whose place card it can be saved.
 */
@Composable
internal fun MapPlaces(
    saved: Map<SavedSlot, Place>,
    onPlaceClick: (Place) -> Unit,
    onFind: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(PvotSpacing.M),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.map_places),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                painter = painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(PvotSpacing.M)) {
            SavedSlot.entries.forEach { slot ->
                val place = saved[slot]
                PlaceCircle(
                    icon = slot.icon,
                    label = stringResource(slot.label),
                    detail = place?.name ?: stringResource(R.string.map_place_not_set),
                    onClick = { if (place != null) onPlaceClick(place) else onFind() },
                    modifier = Modifier.weight(1f),
                )
            }
            PlaceCircle(
                icon = R.drawable.ic_plus,
                label = stringResource(R.string.map_place_add),
                detail = null,
                onClick = onFind,
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.weight(1f))
        }
    }
}

@Composable
private fun PlaceCircle(
    @DrawableRes icon: Int,
    label: String,
    detail: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier =
            modifier
                .clip(MaterialTheme.shapes.medium)
                .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PvotSpacing.S),
    ) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .aspectRatio(1f)
                    .background(colors.onSurface.copy(alpha = CircleFillAlpha), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(32.dp),
            )
        }
        Column(
            modifier = Modifier.padding(bottom = PvotSpacing.XS),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@get:DrawableRes
internal val SavedSlot.icon: Int
    get() =
        when (this) {
            SavedSlot.Home -> R.drawable.ic_home
            SavedSlot.Work -> R.drawable.ic_briefcase
        }

@get:StringRes
internal val SavedSlot.label: Int
    get() =
        when (this) {
            SavedSlot.Home -> R.string.map_place_home
            SavedSlot.Work -> R.string.map_place_work
        }
