package org.prauga.maps.feature.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.prauga.pvot.designsystem.components.PvotGlassCard
import com.prauga.pvot.designsystem.components.PvotGlassIconButton
import com.prauga.pvot.designsystem.effects.PvotGlassBackdrop
import com.prauga.pvot.designsystem.effects.PvotGlassDefaults
import com.prauga.pvot.designsystem.theme.PvotSpacing

/** The controls floating over the map: the toolbar on the right and the search bar below it. */
@Composable
internal fun MapOverlay(
    backdrop: PvotGlassBackdrop,
    onSearch: () -> Unit,
    onLayers: () -> Unit,
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
        MapToolbar(backdrop = backdrop, onLayers = onLayers, onMyLocation = onMyLocation)
        MapSearchBar(
            backdrop = backdrop,
            onClick = onSearch,
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { onSearchBarTop(it.positionInRoot().y) },
        )
    }
}

@Composable
private fun MapToolbar(
    backdrop: PvotGlassBackdrop,
    onLayers: () -> Unit,
    onMyLocation: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PvotSpacing.S)) {
        PvotGlassIconButton(onClick = onLayers, backdrop = backdrop) {
            Icon(
                painter = painterResource(R.drawable.ic_layers),
                contentDescription = stringResource(R.string.map_layers),
            )
        }
        PvotGlassIconButton(onClick = onMyLocation, backdrop = backdrop) {
            Icon(
                painter = painterResource(R.drawable.ic_location),
                contentDescription = stringResource(R.string.map_my_location),
            )
        }
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
