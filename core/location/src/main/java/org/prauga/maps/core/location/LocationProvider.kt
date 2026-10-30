package org.prauga.maps.core.location

import org.prauga.maps.core.model.Coordinates

interface LocationProvider {
    /** A fresh fix from the best enabled provider, or null if none could be had. */
    suspend fun currentLocation(): Coordinates?
}
