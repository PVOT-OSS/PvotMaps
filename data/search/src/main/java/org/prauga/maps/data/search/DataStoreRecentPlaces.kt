/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.data.search

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import org.prauga.maps.core.model.Coordinates
import org.prauga.maps.core.model.Place
import org.prauga.maps.core.model.PlaceKind
import org.prauga.maps.core.search.RecentPlaces

private const val Limit = 10

// Whole places are kept, not their row ids, which change whenever the search data is rebuilt.
@Singleton
internal class DataStoreRecentPlaces
@Inject
constructor(@param:ApplicationContext private val context: Context) : RecentPlaces {

    private val dataStore =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("recent_places") }

    override val places: Flow<List<Place>> =
        dataStore.data.map { preferences -> preferences[Key]?.let(::decode).orEmpty() }

    override suspend fun add(place: Place) {
        dataStore.edit { preferences ->
            val kept = preferences[Key]?.let(::decode).orEmpty().filterNot { it.isSameAs(place) }
            preferences[Key] = encode((listOf(place) + kept).take(Limit))
        }
    }

    private fun Place.isSameAs(other: Place) =
        name == other.name && coordinates == other.coordinates

    private fun encode(places: List<Place>): String =
        JSONArray(
                places.map {
                    JSONObject()
                        .put("name", it.name)
                        .put("localName", it.localName)
                        .put("category", it.category)
                        .put("kind", it.kind.name)
                        .put("lat", it.coordinates.latitude)
                        .put("lon", it.coordinates.longitude)
                        .put("area", it.area)
                        .put("street", it.street)
                        .put("houseNumber", it.houseNumber)
                }
            )
            .toString()

    // A list that can't be read is dropped rather than failing the screen.
    private fun decode(text: String): List<Place> =
        runCatching {
                val array = JSONArray(text)
                List(array.length()) { index ->
                    val json = array.getJSONObject(index)
                    Place(
                        id = -1L - index,
                        name = json.getString("name"),
                        localName = json.optStringOrNull("localName"),
                        category = json.getString("category"),
                        kind =
                            PlaceKind.entries.firstOrNull { it.name == json.optString("kind") }
                                ?: PlaceKind.Other,
                        coordinates = Coordinates(json.getDouble("lat"), json.getDouble("lon")),
                        area = json.optStringOrNull("area"),
                        street = json.optStringOrNull("street"),
                        houseNumber = json.optStringOrNull("houseNumber"),
                    )
                }
            }
            .getOrDefault(emptyList())

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (isNull(name)) null else optString(name)

    private companion object {
        val Key = stringPreferencesKey("places")
    }
}
