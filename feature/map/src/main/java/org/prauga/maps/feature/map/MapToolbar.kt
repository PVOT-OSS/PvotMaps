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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    tilted: Boolean,
    onTilt: () -> Unit,
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
        MapToolbar(
            backdrop = backdrop,
            onMapModes = onMapModes,
            tilted = tilted,
            onTilt = onTilt,
            onMyLocation = onMyLocation,
        )
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
    tilted: Boolean,
    onTilt: () -> Unit,
    onMyLocation: () -> Unit,
) {
    PvotGlassCard(
        backdrop = backdrop,
        cornerRadius = PvotGlassDefaults.CapsuleRadius,
        contentPadding = PaddingValues(vertical = PvotSpacing.XS),
    ) {
        ToolbarButton(onClick = onMapModes) {
            ToolbarIcon(R.drawable.ic_map, stringResource(R.string.map_modes))
        }
        // Shows what a tap switches to, so it reads 3D while the map is flat.
        val tiltLabel = stringResource(if (tilted) R.string.map_flat else R.string.map_tilted)
        ToolbarButton(onClick = onTilt, modifier = Modifier.semantics { contentDescription = tiltLabel }) {
            Text(
                text = stringResource(if (tilted) R.string.map_flat_short else R.string.map_tilted_short),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
        ToolbarButton(onClick = onMyLocation) {
            ToolbarIcon(R.drawable.ic_location, stringResource(R.string.map_my_location))
        }
    }
}

@Composable
private fun ToolbarButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(PvotGlassIconButtonDefaults.Size)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun ToolbarIcon(@DrawableRes icon: Int, contentDescription: String) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.onSurface,
    )
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
