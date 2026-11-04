package org.prauga.maps.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.prauga.maps.core.model.MapMode
import org.prauga.maps.core.model.MapSettings

internal class DataStoreMapSettingsRepository
@Inject
constructor(private val dataStore: DataStore<Preferences>) : MapSettingsRepository {

    override val settings: Flow<MapSettings> =
        dataStore.data.map { preferences ->
            MapSettings(
                // A mode saved by a newer version that this one doesn't know falls back to the
                // default.
                mode =
                    MapMode.entries.firstOrNull { it.name == preferences[Mode] }
                        ?: MapSettings().mode,
                labels = preferences[Labels] ?: MapSettings().labels,
                tilted = preferences[Tilted] ?: MapSettings().tilted,
            )
        }

    override suspend fun setMode(mode: MapMode) {
        dataStore.edit { it[Mode] = mode.name }
    }

    override suspend fun setLabels(labels: Boolean) {
        dataStore.edit { it[Labels] = labels }
    }

    override suspend fun setTilted(tilted: Boolean) {
        dataStore.edit { it[Tilted] = tilted }
    }

    private companion object {
        val Mode = stringPreferencesKey("mode")
        val Labels = booleanPreferencesKey("labels")
        val Tilted = booleanPreferencesKey("tilted")
    }
}
