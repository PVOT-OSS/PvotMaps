/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.engine.maplibre

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.prauga.maps.core.model.Coordinates

private const val Source = "pvot-selected"
private const val Image = "pvot-selected-pin"
private const val Layer = "pvot-selected-pin"

private const val PinColor = 0xFFE5484D.toInt()
private const val HeadRadius = 13f
private const val DotRadius = 5f
private const val PinHeight = 38f
private const val ShadowRadius = 3f

/** Adds the pin that marks a chosen place, drawn over everything but the user's own dot. */
internal fun Style.addSelectedPlaceLayer(density: Float) {
    addImage(Image, pinBitmap(density))
    addSource(GeoJsonSource(Source))
    addLayer(
        SymbolLayer(Layer, Source)
            .withProperties(
                PropertyFactory.iconImage(Image),
                PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            )
    )
}

internal fun Style.setSelectedPlace(place: Coordinates?) {
    val source = getSourceAs<GeoJsonSource>(Source) ?: return
    if (place == null) {
        source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
    } else {
        source.setGeoJson(Point.fromLngLat(place.longitude, place.latitude))
    }
}

// A teardrop whose tip is the bottom-centre of the bitmap, which the layer anchors on the place.
private fun pinBitmap(density: Float): Bitmap {
    val width = ((HeadRadius + ShadowRadius) * 2 * density).toInt()
    val height = ((PinHeight + ShadowRadius) * density).toInt()
    val cx = width / 2f
    val cy = (HeadRadius + ShadowRadius) * density
    val tip = PinHeight * density
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
        val canvas = Canvas(this)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val path =
            Path().apply {
                addCircle(cx, cy, HeadRadius * density, Path.Direction.CW)
                moveTo(cx - HeadRadius * density * 0.6f, cy + HeadRadius * density * 0.8f)
                lineTo(cx, tip)
                lineTo(cx + HeadRadius * density * 0.6f, cy + HeadRadius * density * 0.8f)
                close()
            }
        paint.color = PinColor
        paint.setShadowLayer(ShadowRadius * density, 0f, density, Color.argb(90, 0, 0, 0))
        canvas.drawPath(path, paint)
        paint.clearShadowLayer()
        paint.color = Color.WHITE
        canvas.drawCircle(cx, cy, DotRadius * density, paint)
    }
}
