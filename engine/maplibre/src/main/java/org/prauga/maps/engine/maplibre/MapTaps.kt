/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.engine.maplibre

import android.graphics.RectF
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.geojson.Point
import org.prauga.maps.core.model.Coordinates

/** A named place drawn on the map that the user tapped. */
data class TappedPlace(val name: String, val category: String?, val coordinates: Coordinates)

// The base style's point-of-interest layers: shops, stations, parks, airports and the like.
private val PlaceSourceLayers = setOf("poi", "aerodrome_label")
private const val TapSlopDp = 14f

/** The named place under [point], nearest first, or null when there is none. */
internal fun MapLibreMap.placeAt(point: LatLng, density: Float): TappedPlace? {
    val style = style ?: return null
    val layers =
        style.layers
            .filterIsInstance<SymbolLayer>()
            .filter { it.sourceLayer in PlaceSourceLayers }
            .map { it.id }
            .toTypedArray()
    if (layers.isEmpty()) return null
    val screen = projection.toScreenLocation(point)
    val slop = TapSlopDp * density
    val box = RectF(screen.x - slop, screen.y - slop, screen.x + slop, screen.y + slop)
    val feature =
        queryRenderedFeatures(box, *layers).firstOrNull { it.hasNonNullValueForProperty("name") }
            ?: return null
    val at = (feature.geometry() as? Point)?.let { Coordinates(it.latitude(), it.longitude()) }
    return TappedPlace(
        name =
            feature.getStringProperty("name_en")?.takeIf { it.isNotBlank() }
                ?: feature.getStringProperty("name"),
        category =
            if (feature.hasNonNullValueForProperty("subclass"))
                feature.getStringProperty("subclass")
            else feature.getStringProperty("class"),
        coordinates = at ?: Coordinates(point.latitude, point.longitude),
    )
}
