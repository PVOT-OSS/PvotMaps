package org.prauga.maps.core.settings

import kotlinx.coroutines.flow.Flow
import org.prauga.maps.core.model.MapMode
import org.prauga.maps.core.model.MapSettings

interface MapSettingsRepository {
    val settings: Flow<MapSettings>

    suspend fun setMode(mode: MapMode)

    suspend fun setLabels(labels: Boolean)

    suspend fun setTilted(tilted: Boolean)
}
