/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.engine.maplibre

import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.prauga.maps.core.model.Coordinates

private const val Source = "pvot-route"
private const val Casing = "pvot-route-casing"
private const val Line = "pvot-route-line"
private const val LineColor = "#2F7BF5"
private const val CasingColor = "#1B4FA8"

/** Adds the route line, under the map's labels so street names stay readable on top of it. */
internal fun Style.addRouteLayer() {
    addSource(GeoJsonSource(Source))
    val casing =
        LineLayer(Casing, Source)
            .withProperties(
                PropertyFactory.lineColor(CasingColor),
                PropertyFactory.lineWidth(widthByZoom(7f, 14f)),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
    val line =
        LineLayer(Line, Source)
            .withProperties(
                PropertyFactory.lineColor(LineColor),
                PropertyFactory.lineWidth(widthByZoom(4.5f, 10f)),
                PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
    val firstLabel = layers.firstOrNull { it is SymbolLayer }?.id
    if (firstLabel != null) {
        addLayerBelow(casing, firstLabel)
        addLayerBelow(line, firstLabel)
    } else {
        addLayer(casing)
        addLayer(line)
    }
}

internal fun Style.setRoute(shape: List<Coordinates>?) {
    val source = getSourceAs<GeoJsonSource>(Source) ?: return
    if (shape.isNullOrEmpty()) {
        source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
    } else {
        source.setGeoJson(
            LineString.fromLngLats(shape.map { Point.fromLngLat(it.longitude, it.latitude) })
        )
    }
}

private fun widthByZoom(atCity: Float, atStreet: Float) =
    Expression.interpolate(
        Expression.exponential(1.5f),
        Expression.zoom(),
        Expression.stop(10, atCity),
        Expression.stop(18, atStreet),
    )
