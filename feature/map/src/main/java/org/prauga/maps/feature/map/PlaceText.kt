/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.feature.map

import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import kotlin.math.roundToInt
import org.prauga.maps.core.model.Place
import org.prauga.maps.core.model.PlaceKind

/** "Cafe", "Bus stop": the OpenStreetMap value a place was found by, readable. */
internal val Place.categoryLabel: String
    get() = category.replace('_', ' ').replaceFirstChar { it.uppercase() }

/** What sits under a place's name in a list: its kind of place and the area it is in. */
internal val Place.summary: String
    get() = listOfNotNull(categoryLabel, area?.takeIf { it != name }).joinToString(" · ")

/** "12, 4th Cross Road", when OpenStreetMap has an address for it. */
internal val Place.address: String?
    get() = listOfNotNull(houseNumber, street).joinToString(", ").ifEmpty { null }

@get:DrawableRes
internal val PlaceKind.icon: Int
    get() =
        when (this) {
            PlaceKind.Place -> R.drawable.ic_kind_place
            PlaceKind.Street -> R.drawable.ic_kind_street
            PlaceKind.Transport -> R.drawable.ic_kind_transport
            PlaceKind.Nature -> R.drawable.ic_kind_nature
            PlaceKind.Food -> R.drawable.ic_kind_food
            PlaceKind.Shop -> R.drawable.ic_kind_shop
            PlaceKind.Health -> R.drawable.ic_kind_health
            PlaceKind.Education -> R.drawable.ic_kind_education
            PlaceKind.Worship -> R.drawable.ic_kind_worship
            PlaceKind.Sight -> R.drawable.ic_kind_sight
            PlaceKind.Leisure -> R.drawable.ic_kind_leisure
            PlaceKind.Office -> R.drawable.ic_briefcase
            PlaceKind.Amenity,
            PlaceKind.Other -> R.drawable.ic_kind_other
        }

@Composable
internal fun distanceText(meters: Double): String = formatDistance(LocalResources.current, meters)

/** "350 m" or "2.4 km", rounded the way people say it. */
internal fun formatDistance(resources: Resources, meters: Double): String =
    if (meters < 1000) {
        resources.getString(
            R.string.map_distance_m,
            ((meters / 10).roundToInt() * 10).coerceAtLeast(10),
        )
    } else {
        resources.getString(R.string.map_distance_km, meters / 1000)
    }
