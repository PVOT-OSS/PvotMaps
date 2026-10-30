package org.prauga.maps.core.location

import kotlinx.coroutines.flow.Flow
import org.prauga.maps.core.model.Coordinates

interface LocationProvider {
    /** A fresh fix from the best enabled provider, or null if none could be had. */
    suspend fun currentLocation(): Coordinates?

    /** Fixes as the user moves, while collected. Ends at once if no provider is enabled. */
    fun locationUpdates(): Flow<Coordinates>
}
