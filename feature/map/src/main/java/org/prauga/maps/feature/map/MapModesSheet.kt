package org.prauga.maps.feature.map

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.prauga.pvot.designsystem.components.PvotBottomSheet
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.maps.core.model.MapMode

private val CardShape = RoundedCornerShape(16.dp)
private const val CardFillAlpha = 0.06f

@Composable
internal fun MapModesSheet(
    visible: Boolean,
    mode: MapMode,
    onModeChange: (MapMode) -> Unit,
    labels: Boolean,
    onLabelsChange: (Boolean) -> Unit,
    onDismissRequest: () -> Unit,
) {
    PvotBottomSheet(
        visible = visible,
        onDismissRequest = onDismissRequest,
        title = stringResource(R.string.map_modes),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(PvotSpacing.M)) {
            MapMode.entries.forEach { entry ->
                MapModeCard(
                    mode = entry,
                    selected = entry == mode,
                    onClick = { onModeChange(entry) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = PvotSpacing.L)
                .clip(CardShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = CardFillAlpha))
                .toggleable(value = labels, role = Role.Switch, onValueChange = onLabelsChange)
                .padding(horizontal = PvotSpacing.L, vertical = PvotSpacing.M),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.map_labels),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            // PvotLib has no switch yet; Material's takes its colours from PvotAppTheme.
            Switch(checked = labels, onCheckedChange = null)
        }
    }
}

@Composable
private fun MapModeCard(
    mode: MapMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(CardShape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PvotSpacing.S),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.4f)
                .background(colors.onSurface.copy(alpha = CardFillAlpha), CardShape)
                .border(
                    BorderStroke(2.dp, if (selected) colors.primary else Color.Transparent),
                    CardShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(mode.icon),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = if (selected) colors.primary else colors.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(mode.label),
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
        )
    }
}

@get:DrawableRes
private val MapMode.icon: Int
    get() = when (this) {
        MapMode.Explore -> R.drawable.ic_map
        MapMode.Driving -> R.drawable.ic_steering_wheel
    }

@get:StringRes
private val MapMode.label: Int
    get() = when (this) {
        MapMode.Explore -> R.string.map_mode_explore
        MapMode.Driving -> R.string.map_mode_driving
    }
