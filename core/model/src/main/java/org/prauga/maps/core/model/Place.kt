/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.core.model

/**
 * A named place from the offline search data: a shop, a station, a park, a street, a neighbourhood.
 */
data class Place(
    val id: Long,
    val name: String,
    /** The name in the local script (Kannada in Bengaluru), when it differs from [name]. */
    val localName: String?,
    /** The OpenStreetMap value it was found by, such as "cafe" or "bus_stop". */
    val category: String,
    val kind: PlaceKind,
    val coordinates: Coordinates,
    /** The neighbourhood or suburb it is in. */
    val area: String?,
    val street: String?,
    val houseNumber: String?,
)

enum class PlaceKind {
    Place,
    Street,
    Transport,
    Nature,
    Food,
    Shop,
    Health,
    Education,
    Worship,
    Sight,
    Leisure,
    Office,
    Amenity,
    Other,
}
