/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.prauga.pvot.designsystem.components.PvotBottomSheetDefaults
import com.prauga.pvot.designsystem.components.PvotPersistentSheet
import com.prauga.pvot.designsystem.components.PvotPersistentSheetState
import com.prauga.pvot.designsystem.components.PvotSheetDetent
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.button.PvotButtonStyle
import com.prauga.pvot.designsystem.effects.PvotGlassBackdrop
import com.prauga.pvot.designsystem.theme.PvotSpacing
import kotlinx.coroutines.launch
import org.prauga.maps.core.model.Coordinates
import org.prauga.maps.core.model.Place
import org.prauga.maps.core.model.TravelMode
import org.prauga.maps.core.model.distanceTo
import org.prauga.maps.core.search.SavedSlot

private val SearchRowHeight = 44.dp
private const val FieldFillAlpha = 0.08f
private val KindIconSize = 40.dp

// From the bar's top edge to the field: the handle and the space either side of it.
private val HandleSpace = PvotSpacing.XS * 2 + PvotBottomSheetDefaults.HandleHeight

/** The collapsed floating search bar: the field, centred, with the handle in the space above it. */
internal val SearchPeekHeight = HandleSpace + SearchRowHeight + HandleSpace

/**
 * Search, in a frosted sheet floating over the map. Collapsed it reads as a search bar with a drag
 * handle on top; drag the handle, or start typing, and the same card opens into the sheet, listing
 * places as you type and, once one is chosen, a card about it.
 */
@Composable
internal fun MapSearchSheet(
    state: PvotPersistentSheetState,
    backdrop: PvotGlassBackdrop,
    query: String,
    onQueryChange: (String) -> Unit,
    results: SearchResults,
    recents: List<Place>,
    selected: Place?,
    onPlaceClick: (Place) -> Unit,
    onCloseSelected: () -> Unit,
    plan: RoutePlan,
    onDirections: () -> Unit,
    onModeChange: (TravelMode) -> Unit,
    onStartNavigation: () -> Unit,
    onCloseRoute: () -> Unit,
    userPosition: Coordinates?,
    saved: Map<SavedSlot, Place>,
    onSave: (SavedSlot, Place) -> Unit,
    onRemoveSaved: (SavedSlot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val searchFocus = remember { FocusRequester() }
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(focused) { if (focused) state.animateTo(PvotSheetDetent.Expanded) }
    LaunchedEffect(state.currentDetent) {
        if (state.currentDetent == PvotSheetDetent.Collapsed) focusManager.clearFocus()
    }
    BackHandler(enabled = state.targetDetent != PvotSheetDetent.Collapsed) {
        focusManager.clearFocus()
        scope.launch { state.animateTo(PvotSheetDetent.Collapsed) }
    }
    // Registered last, so going back closes the place card before it collapses the sheet.
    BackHandler(enabled = selected != null, onBack = onCloseSelected)
    BackHandler(enabled = plan != RoutePlan.None, onBack = onCloseRoute)

    val open = state.targetDetent != PvotSheetDetent.Collapsed
    PvotPersistentSheet(
        state = state,
        peekHeight = SearchPeekHeight,
        modifier = modifier,
        floating = true,
        backdrop = backdrop,
    ) {
        SearchRow(
            query = query,
            onQueryChange = onQueryChange,
            interactionSource = interactionSource,
            focusRequester = searchFocus,
        )
        // Collapsed, the card ends at the search row; anything under it would peek out.
        AnimatedVisibility(visible = open, enter = fadeIn(), exit = fadeOut()) {
            Column(modifier = Modifier.padding(top = PvotSpacing.L)) {
                when {
                    plan != RoutePlan.None ->
                        RoutePreview(plan, onModeChange, onStartNavigation, onCloseRoute)
                    selected != null ->
                        PlaceCard(
                            selected,
                            userPosition,
                            saved,
                            onDirections,
                            onSave,
                            onRemoveSaved,
                            onCloseSelected,
                        )
                    query.isBlank() ->
                        PlacesAndRecents(
                            recents,
                            saved,
                            userPosition,
                            onPlaceClick,
                            onFind = { searchFocus.requestFocus() },
                        )
                    else -> SearchResultList(results, userPosition, onPlaceClick)
                }
            }
        }
    }
}

@Composable
private fun PlacesAndRecents(
    recents: List<Place>,
    saved: Map<SavedSlot, Place>,
    userPosition: Coordinates?,
    onPlaceClick: (Place) -> Unit,
    onFind: () -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(PvotSpacing.XS)) {
        item { MapPlaces(saved, onPlaceClick, onFind) }
        if (recents.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.map_recents),
                    style =
                        MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = PvotSpacing.L),
                )
            }
            items(recents, key = { it.name to it.coordinates }) { place ->
                SearchResultRow(place, userPosition, onClick = { onPlaceClick(place) })
            }
        }
    }
}

@Composable
private fun SearchResultList(
    results: SearchResults,
    userPosition: Coordinates?,
    onPlaceClick: (Place) -> Unit,
) {
    when (results) {
        SearchResults.Idle -> Unit
        SearchResults.Unavailable -> SearchMessage(stringResource(R.string.map_search_unavailable))
        is SearchResults.Found ->
            if (results.places.isEmpty()) {
                SearchMessage(stringResource(R.string.map_search_no_results))
            } else {
                LazyColumn(
                    contentPadding = WindowInsets.ime.asPaddingValues(),
                    verticalArrangement = Arrangement.spacedBy(PvotSpacing.XS),
                ) {
                    items(results.places, key = { it.id }) { place ->
                        SearchResultRow(place, userPosition, onClick = { onPlaceClick(place) })
                    }
                }
            }
    }
}

@Composable
private fun SearchResultRow(place: Place, userPosition: Coordinates?, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(PvotSpacing.L))
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = PvotSpacing.S),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PvotSpacing.M),
    ) {
        KindIcon(place)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = place.name,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = place.summary,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (userPosition != null) {
            Text(
                text = distanceText(userPosition.distanceTo(place.coordinates)),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaceCard(
    place: Place,
    userPosition: Coordinates?,
    saved: Map<SavedSlot, Place>,
    onDirections: () -> Unit,
    onSave: (SavedSlot, Place) -> Unit,
    onRemoveSaved: (SavedSlot) -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(PvotSpacing.XS)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = place.name,
                    style =
                        MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.onSurface,
                )
                place.localName?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.map_place_close),
                tint = colors.onSurfaceVariant,
                modifier =
                    Modifier.clip(CircleShape)
                        .clickable(role = Role.Button, onClick = onClose)
                        .padding(PvotSpacing.XS),
            )
        }
        val distance = userPosition?.let { distanceText(it.distanceTo(place.coordinates)) }
        Text(
            text = listOfNotNull(place.summary, distance).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )
        place.address?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
        }
        PvotButton(
            text = stringResource(R.string.map_directions),
            onClick = onDirections,
            modifier = Modifier.fillMaxWidth().padding(top = PvotSpacing.M),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(PvotSpacing.S)) {
            SavedSlot.entries.forEach { slot ->
                val isThis =
                    saved[slot]?.let {
                        it.name == place.name && it.coordinates == place.coordinates
                    } == true
                PvotButton(
                    text =
                        stringResource(
                            if (isThis) R.string.map_place_remove_saved
                            else R.string.map_place_set_saved,
                            stringResource(slot.label),
                        ),
                    onClick = { if (isThis) onRemoveSaved(slot) else onSave(slot, place) },
                    style = PvotButtonStyle.Text,
                    icon = painterResource(slot.icon),
                )
            }
        }
    }
}

@Composable
private fun KindIcon(place: Place) {
    Box(
        modifier =
            Modifier.size(KindIconSize)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = FieldFillAlpha * 2),
                    CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(place.kind.icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SearchMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = PvotSpacing.S),
    )
}

// Its own filled capsule inside the glass bar, the same collapsed and open.
@Composable
private fun SearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    interactionSource: MutableInteractionSource,
    focusRequester: FocusRequester,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(SearchRowHeight)
                .background(colors.onSurface.copy(alpha = FieldFillAlpha), CircleShape)
                .padding(horizontal = PvotSpacing.M)
                .clickable(interactionSource = null, indication = null) {
                    focusRequester.requestFocus()
                },
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
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
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
                modifier =
                    Modifier.clip(CircleShape).clickable(role = Role.Button) { onQueryChange("") },
            )
        }
    }
}
