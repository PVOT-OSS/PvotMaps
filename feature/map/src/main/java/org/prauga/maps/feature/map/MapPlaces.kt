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
import androidx.compose.ui.unit.dp
import com.prauga.pvot.designsystem.theme.PvotSpacing

private const val CircleFillAlpha = 0.08f

/**
 * Shortcuts to the user's own places. Saving them comes with on-device storage; for now the circles
 * are stubs that do nothing.
 */
@Composable
internal fun MapPlaces(modifier: Modifier = Modifier) {
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
            PlaceCircle(R.drawable.ic_home, R.string.map_place_home, Modifier.weight(1f))
            PlaceCircle(R.drawable.ic_briefcase, R.string.map_place_work, Modifier.weight(1f))
            PlaceCircle(R.drawable.ic_plus, R.string.map_place_add, Modifier.weight(1f))
            Box(Modifier.weight(1f))
        }
    }
}

@Composable
private fun PlaceCircle(
    @DrawableRes icon: Int,
    @StringRes label: Int,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier.clip(MaterialTheme.shapes.medium).clickable(role = Role.Button) {},
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
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurface,
            modifier = Modifier.padding(bottom = PvotSpacing.XS),
        )
    }
}
