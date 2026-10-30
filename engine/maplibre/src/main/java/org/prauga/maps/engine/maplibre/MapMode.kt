package org.prauga.maps.engine.maplibre

/** What the map is drawn for. */
enum class MapMode {
    /** Everything: places, parks, 3D buildings. */
    Explore,

    /** The roads, with only places a driver stops at (fuel, parking) and flat buildings. */
    Driving,
}
