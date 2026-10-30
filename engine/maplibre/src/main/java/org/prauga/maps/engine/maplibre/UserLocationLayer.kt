package org.prauga.maps.engine.maplibre

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point
import org.prauga.maps.core.model.UserPosition

private const val Source = "pvot-user"
private const val DotImage = "pvot-user-dot"
private const val ConeImage = "pvot-user-cone"
private const val DotLayer = "pvot-user-dot"
private const val ConeLayer = "pvot-user-cone"
private const val Heading = "heading"

private const val Accent = 0xFF1E7CF2.toInt()
private const val DotRadius = 7f
private const val RingRadius = 10f
private const val ShadowRadius = 3f
private const val ConeRadius = 64f
private const val ConeSweep = 70f

/** Adds the blue dot and its heading cone above everything else in the style. */
internal fun Style.addUserLocationLayer(density: Float) {
    addImage(DotImage, dotBitmap(density))
    addImage(ConeImage, coneBitmap(density))
    addSource(GeoJsonSource(Source))
    addLayer(
        SymbolLayer(ConeLayer, Source)
            .withFilter(Expression.has(Heading))
            .withProperties(
                PropertyFactory.iconImage(ConeImage),
                PropertyFactory.iconRotate(Expression.get(Heading)),
                PropertyFactory.iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                PropertyFactory.iconPitchAlignment(Property.ICON_PITCH_ALIGNMENT_MAP),
                PropertyFactory.iconAllowOverlap(true),
                PropertyFactory.iconIgnorePlacement(true),
            ),
    )
    addLayer(
        SymbolLayer(DotLayer, Source).withProperties(
            PropertyFactory.iconImage(DotImage),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        ),
    )
}

internal fun Style.setUserPosition(position: UserPosition?) {
    val source = getSourceAs<GeoJsonSource>(Source) ?: return
    if (position == null) {
        source.setGeoJson(FeatureCollection.fromFeatures(emptyList()))
        return
    }
    val point = Point.fromLngLat(position.coordinates.longitude, position.coordinates.latitude)
    val feature = Feature.fromGeometry(point)
    position.heading?.let { feature.addNumberProperty(Heading, it) }
    source.setGeoJson(feature)
}

private fun dotBitmap(density: Float): Bitmap {
    val size = ((RingRadius + ShadowRadius) * 2 * density).toInt()
    val center = size / 2f
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        val canvas = Canvas(this)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.WHITE
        paint.setShadowLayer(ShadowRadius * density, 0f, 0f, Color.argb(90, 0, 0, 0))
        canvas.drawCircle(center, center, RingRadius * density, paint)
        paint.clearShadowLayer()
        paint.color = Accent
        canvas.drawCircle(center, center, DotRadius * density, paint)
    }
}

// Points up (north); the layer turns it by the heading.
private fun coneBitmap(density: Float): Bitmap {
    val radius = ConeRadius * density
    val size = (radius * 2).toInt()
    val center = size / 2f
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = RadialGradient(
            center,
            center,
            radius,
            intArrayOf(Color.argb(150, Color.red(Accent), Color.green(Accent), Color.blue(Accent)), Color.TRANSPARENT),
            null,
            Shader.TileMode.CLAMP,
        )
        val bounds = RectF(center - radius, center - radius, center + radius, center + radius)
        Canvas(this).drawArc(bounds, -90f - ConeSweep / 2, ConeSweep, true, paint)
    }
}
