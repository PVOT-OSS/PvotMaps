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
import org.json.JSONObject
import org.prauga.maps.core.model.Place
import org.prauga.maps.core.search.SavedPlaces
import org.prauga.maps.core.search.SavedSlot

@Singleton
internal class DataStoreSavedPlaces
@Inject
constructor(@param:ApplicationContext private val context: Context) : SavedPlaces {

    private val dataStore =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("saved_places") }

    override val places: Flow<Map<SavedSlot, Place>> =
        dataStore.data.map { preferences ->
            SavedSlot.entries
                .mapNotNull { slot ->
                    preferences[slot.key]
                        ?.let {
                            runCatching { PlaceJson.decode(JSONObject(it), slot.id) }.getOrNull()
                        }
                        ?.let { slot to it }
                }
                .toMap()
        }

    override suspend fun save(slot: SavedSlot, place: Place) {
        dataStore.edit { it[slot.key] = PlaceJson.encode(place).toString() }
    }

    override suspend fun remove(slot: SavedSlot) {
        dataStore.edit { it.remove(slot.key) }
    }

    private val SavedSlot.key
        get() = stringPreferencesKey(name.lowercase())

    // Out of the way of search rows (positive) and recents (from -1 down).
    private val SavedSlot.id
        get() = -1000L - ordinal
}
