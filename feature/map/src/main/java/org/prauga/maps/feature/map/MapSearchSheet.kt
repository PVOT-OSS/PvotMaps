package org.prauga.maps.feature.map

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.prauga.pvot.designsystem.components.PvotPersistentSheet
import com.prauga.pvot.designsystem.components.PvotPersistentSheetState
import com.prauga.pvot.designsystem.components.PvotSheetDetent
import com.prauga.pvot.designsystem.components.textfield.PvotTextField
import com.prauga.pvot.designsystem.theme.PvotSpacing
import kotlinx.coroutines.launch

/** The drag handle, the search field and a little room under it. */
internal val SearchPeekHeight = 96.dp

/**
 * Search, in a sheet that stays over the map. Collapsed it is just the search field; typing opens
 * it fully. Finding places comes with on-device search; for now it only takes the query.
 */
@Composable
internal fun MapSearchSheet(
    state: PvotPersistentSheetState,
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

    PvotPersistentSheet(state = state, peekHeight = SearchPeekHeight, modifier = modifier) {
        PvotTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = stringResource(R.string.map_search_hint),
            leadingIcon = {
                Icon(painter = painterResource(R.drawable.ic_search), contentDescription = null)
            },
            trailingIcon = if (query.isEmpty()) null else {
                {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.map_search_clear),
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { query = "" },
                    )
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = CircleShape,
            interactionSource = interactionSource,
        )
        // Collapsed, the peek ends at the field; anything under it would show through the gesture bar.
        AnimatedVisibility(
            visible = state.targetDetent != PvotSheetDetent.Collapsed,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Text(
                text = stringResource(R.string.map_search_not_ready),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = PvotSpacing.S, vertical = PvotSpacing.L),
            )
        }
    }
}
