/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.prauga.maps.core.model.Coordinates
import org.prauga.maps.core.model.Place
import org.prauga.maps.core.model.PlaceKind
import org.prauga.maps.core.model.distanceTo
import org.prauga.maps.core.search.PlaceSearch
import org.prauga.maps.core.search.RecentPlaces
import org.prauga.maps.core.search.SearchText
import org.prauga.maps.engine.maplibre.TappedPlace

internal sealed interface SearchResults {
    /** Nothing typed yet. */
    data object Idle : SearchResults

    /** No search data is installed on the device. */
    data object Unavailable : SearchResults

    data class Found(val places: List<Place>) : SearchResults
}

private data class SearchRequest(val query: String, val near: Coordinates?)

private const val TypingPauseMillis = 120L

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class SearchViewModel
@Inject
constructor(private val placeSearch: PlaceSearch, private val recentPlaces: RecentPlaces) :
    ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val request = MutableStateFlow(SearchRequest("", null))

    val results: StateFlow<SearchResults> =
        request
            .debounce(TypingPauseMillis)
            .mapLatest { (query, near) ->
                when {
                    SearchText.words(query).isEmpty() -> SearchResults.Idle
                    !placeSearch.isAvailable() -> SearchResults.Unavailable
                    else -> SearchResults.Found(placeSearch.search(query, near))
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(StopMillis), SearchResults.Idle)

    /** Places picked lately, newest first. */
    val recents: StateFlow<List<Place>> =
        recentPlaces.places.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(StopMillis),
            emptyList(),
        )

    private val _selected = MutableStateFlow<Place?>(null)
    val selected: StateFlow<Place?> = _selected.asStateFlow()

    /** Searches for [query], ranking equally good matches by how close they are to [near]. */
    fun onQueryChange(query: String, near: Coordinates?) {
        _query.value = query
        request.value = SearchRequest(query, near)
    }

    fun select(place: Place, remember: Boolean = true) {
        _selected.value = place
        if (remember) viewModelScope.launch { recentPlaces.add(place) }
    }

    /**
     * The search data's entry for a place tapped on the map, which knows its area and local name,
     * or one made from what the map knows when the search data has no match nearby.
     */
    suspend fun resolve(tapped: TappedPlace): Place {
        val folded = SearchText.fold(tapped.name)
        return placeSearch.search(tapped.name, tapped.coordinates, MatchCandidates).firstOrNull {
            SearchText.fold(it.name) == folded &&
                it.coordinates.distanceTo(tapped.coordinates) < MatchMeters
        }
            ?: Place(
                id = TappedId,
                name = tapped.name,
                localName = null,
                category = tapped.category ?: "place",
                kind = PlaceKind.Other,
                coordinates = tapped.coordinates,
                area = null,
                street = null,
                houseNumber = null,
            )
    }

    /** A place for a point the user long-pressed, which has no name of its own. */
    fun droppedPin(at: Coordinates) =
        Place(
            id = DroppedId,
            name = "%.5f, %.5f".format(Locale.ROOT, at.latitude, at.longitude),
            localName = null,
            category = DroppedCategory,
            kind = PlaceKind.Other,
            coordinates = at,
            area = null,
            street = null,
            houseNumber = null,
        )

    fun clearSelection() {
        _selected.value = null
    }

    private companion object {
        const val StopMillis = 5_000L
        const val MatchCandidates = 10
        const val MatchMeters = 250.0
        const val TappedId = -100L
        const val DroppedId = -101L
        const val DroppedCategory = "dropped_pin"
    }
}
