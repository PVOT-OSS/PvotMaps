/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
import org.prauga.maps.core.model.Coordinates
import org.prauga.maps.core.model.Place
import org.prauga.maps.core.search.PlaceSearch
import org.prauga.maps.core.search.SearchText

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
internal class SearchViewModel @Inject constructor(private val placeSearch: PlaceSearch) :
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

    private val _selected = MutableStateFlow<Place?>(null)
    val selected: StateFlow<Place?> = _selected.asStateFlow()

    /** Searches for [query], ranking equally good matches by how close they are to [near]. */
    fun onQueryChange(query: String, near: Coordinates?) {
        _query.value = query
        request.value = SearchRequest(query, near)
    }

    fun select(place: Place) {
        _selected.value = place
    }

    fun clearSelection() {
        _selected.value = null
    }

    private companion object {
        const val StopMillis = 5_000L
    }
}
