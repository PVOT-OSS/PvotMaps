package org.prauga.maps.feature.map

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.prauga.pvot.designsystem.components.PvotGlassCard
import com.prauga.pvot.designsystem.components.PvotGlassIconButtonDefaults
import com.prauga.pvot.designsystem.effects.PvotGlassBackdrop
import com.prauga.pvot.designsystem.effects.PvotGlassDefaults
import com.prauga.pvot.designsystem.theme.PvotSpacing

/** The controls floating over the map: the toolbar on the right and the search bar below it. */
@Composable
internal fun MapOverlay(
    backdrop: PvotGlassBackdrop,
    onSearch: () -> Unit,
    onMapModes: () -> Unit,
    onMyLocation: () -> Unit,
    onSearchBarTop: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .navigationBarsPadding()
            .padding(PvotSpacing.L),
        verticalArrangement = Arrangement.spacedBy(PvotSpacing.M),
        horizontalAlignment = Alignment.End,
    ) {
        MapToolbar(backdrop = backdrop, onMapModes = onMapModes, onMyLocation = onMyLocation)
        MapSearchBar(
            backdrop = backdrop,
            onClick = onSearch,
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { onSearchBarTop(it.positionInRoot().y) },
        )
    }
}

// One glass pill holding all the map buttons, like a single control rather than loose circles.
@Composable
private fun MapToolbar(
    backdrop: PvotGlassBackdrop,
    onMapModes: () -> Unit,
    onMyLocation: () -> Unit,
) {
    PvotGlassCard(
        backdrop = backdrop,
        cornerRadius = PvotGlassDefaults.CapsuleRadius,
        contentPadding = PaddingValues(vertical = PvotSpacing.XS),
    ) {
        ToolbarButton(
            icon = R.drawable.ic_map,
            contentDescription = stringResource(R.string.map_modes),
            onClick = onMapModes,
        )
        ToolbarButton(
            icon = R.drawable.ic_location,
            contentDescription = stringResource(R.string.map_my_location),
            onClick = onMyLocation,
        )
    }
}

@Composable
private fun ToolbarButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(PvotGlassIconButtonDefaults.Size)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun MapSearchBar(
    backdrop: PvotGlassBackdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PvotGlassCard(
        backdrop = backdrop,
        modifier = modifier,
        onClick = onClick,
        cornerRadius = PvotGlassDefaults.CapsuleRadius,
        contentPadding = PaddingValues(horizontal = PvotSpacing.L, vertical = PvotSpacing.L),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PvotSpacing.M),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.map_search_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
