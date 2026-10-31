package org.prauga.maps.feature.map

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.prauga.pvot.designsystem.components.PvotBottomSheetDefaults
import com.prauga.pvot.designsystem.components.PvotPersistentSheet
import com.prauga.pvot.designsystem.components.PvotPersistentSheetState
import com.prauga.pvot.designsystem.components.PvotSheetDetent
import com.prauga.pvot.designsystem.effects.LocalPvotGlassIntensity
import com.prauga.pvot.designsystem.effects.PvotGlassBackdrop
import com.prauga.pvot.designsystem.theme.PvotSpacing
import kotlinx.coroutines.launch

private val SearchRowHeight = 44.dp
private const val FieldFillAlpha = 0.08f

// From the bar's top edge to the field: the handle and the space either side of it.
private val HandleSpace = PvotSpacing.XS * 2 + PvotBottomSheetDefaults.HandleHeight

/** The collapsed floating search bar: the field, centred, with the handle in the space above it. */
internal val SearchPeekHeight = HandleSpace + SearchRowHeight + HandleSpace

/**
 * Search, in a frosted sheet floating over the map. Collapsed it reads as a search bar with a
 * drag handle on top; drag the handle, or start typing, and the same card opens into the sheet.
 * Finding places comes with on-device search; for now it only takes the query.
 */
@Composable
internal fun MapSearchSheet(
    state: PvotPersistentSheetState,
    backdrop: PvotGlassBackdrop,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(focused) {
        if (focused) state.animateTo(PvotSheetDetent.Expanded)
    }
    LaunchedEffect(state.currentDetent) {
        if (state.currentDetent == PvotSheetDetent.Collapsed) focusManager.clearFocus()
    }
    BackHandler(enabled = state.targetDetent != PvotSheetDetent.Collapsed) {
        focusManager.clearFocus()
        scope.launch { state.animateTo(PvotSheetDetent.Collapsed) }
    }

    val open = state.targetDetent != PvotSheetDetent.Collapsed
    // Fully frosted, not liquid: a calm dark (or light) slab that the map only blurs through.
    CompositionLocalProvider(LocalPvotGlassIntensity provides 0f) {
        PvotPersistentSheet(
            state = state,
            peekHeight = SearchPeekHeight,
            modifier = modifier,
            floating = true,
            backdrop = backdrop,
        ) {
            SearchRow(
                query = query,
                onQueryChange = { query = it },
                interactionSource = interactionSource,
            )
            // Collapsed, the card ends at the search row; anything under it would peek out.
            AnimatedVisibility(visible = open, enter = fadeIn(), exit = fadeOut()) {
                Column(modifier = Modifier.padding(top = PvotSpacing.L)) {
                    if (query.isEmpty()) {
                        MapPlaces()
                    } else {
                        Text(
                            text = stringResource(R.string.map_search_not_ready),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = PvotSpacing.S),
                        )
                    }
                }
            }
        }
    }
}

// Its own filled capsule inside the glass bar, the same collapsed and open.
@Composable
private fun SearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    interactionSource: MutableInteractionSource,
) {
    val focusRequester = remember { FocusRequester() }
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(SearchRowHeight)
            .background(colors.onSurface.copy(alpha = FieldFillAlpha), CircleShape)
            .padding(horizontal = PvotSpacing.M)
            .clickable(interactionSource = null, indication = null) { focusRequester.requestFocus() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PvotSpacing.M),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            tint = colors.onSurfaceVariant,
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    text = stringResource(R.string.map_search_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                interactionSource = interactionSource,
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.map_search_clear),
                tint = colors.onSurfaceVariant,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button) { onQueryChange("") },
            )
        }
    }
}
