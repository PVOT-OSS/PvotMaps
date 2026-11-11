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
import org.prauga.maps.core.model.Place
import org.prauga.maps.core.search.RecentPlaces

private const val Limit = 10

@Singleton
internal class DataStoreRecentPlaces
@Inject
constructor(@param:ApplicationContext private val context: Context) : RecentPlaces {

    private val dataStore =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("recent_places") }

    override val places: Flow<List<Place>> =
        dataStore.data.map { preferences ->
            preferences[Key]?.let { PlaceJson.decodeList(it, FirstId) }.orEmpty()
        }

    override suspend fun add(place: Place) {
        dataStore.edit { preferences ->
            val kept =
                preferences[Key]
                    ?.let { PlaceJson.decodeList(it, FirstId) }
                    .orEmpty()
                    .filterNot { it.isSameAs(place) }
            preferences[Key] = PlaceJson.encodeList((listOf(place) + kept).take(Limit))
        }
    }

    private fun Place.isSameAs(other: Place) =
        name == other.name && coordinates == other.coordinates

    private companion object {
        const val FirstId = -1L
        val Key = stringPreferencesKey("places")
    }
}
