package org.prauga.maps.core.model

/** How the user last left the map, kept between launches. */
data class MapSettings(
    val mode: MapMode = MapMode.Explore,
    val labels: Boolean = true,
    val tilted: Boolean = false,
)
