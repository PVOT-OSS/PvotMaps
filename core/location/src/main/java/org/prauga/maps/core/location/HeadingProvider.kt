package org.prauga.maps.core.location

import kotlinx.coroutines.flow.Flow

interface HeadingProvider {
    /**
     * Which way the top of the screen points, in degrees clockwise from magnetic north. Empty
     * without a compass.
     */
    fun heading(): Flow<Float>
}
