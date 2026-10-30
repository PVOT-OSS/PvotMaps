package org.prauga.maps.engine.maplibre

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

private const val ReliefSource = "ne2_shaded"
private const val ReliefLayer = "earth_relief"
private const val Ocean = "#163a70"
private const val DarkEarthLabel = "#e6e6e6"

// Below EarthFadeEnd the map reads as a planet: Natural Earth relief for land, deep blue oceans.
private const val EarthFadeStart = 5
private const val EarthFadeEnd = 8

// The globe is switched on only through the style JSON; MapLibre has no runtime projection API.
internal suspend fun fetchGlobeStyle(styleUri: String, dark: Boolean): String =
    withContext(Dispatchers.IO) {
        val style = JSONObject(URL(styleUri).openStream().bufferedReader().use { it.readText() })
        style.put("projection", JSONObject().put("type", "globe"))
        if (style.getJSONObject("sources").has(ReliefSource)) {
            style.put("layers", style.getJSONArray("layers").withEarth(dark))
        }
        style.toString()
    }

private fun JSONArray.withEarth(dark: Boolean): JSONArray {
    val layers = JSONArray()
    for (i in 0 until length()) {
        val layer = getJSONObject(i)
        if (layer.optString("source") == ReliefSource) continue
        if (layer.getString("id") == "water") layer.fadeWaterFromOcean()
        when (layer.optString("source-layer")) {
            "boundary" -> layer.paint().put("line-opacity", zoomRamp(3, 0, 6, 1))
            "place" -> if (dark) layer.brightenLabel()
        }
        layers.put(layer)
        if (layer.getString("type") == "background") layers.put(reliefLayer(dark))
    }
    return layers
}

private fun reliefLayer(dark: Boolean) = JSONObject()
    .put("id", ReliefLayer)
    .put("type", "raster")
    .put("source", ReliefSource)
    .put("maxzoom", EarthFadeEnd)
    .put(
        "paint",
        JSONObject()
            .put("raster-opacity", earthRamp(1, 0))
            .put("raster-saturation", 0.2)
            .put("raster-brightness-max", if (dark) 0.75 else 0.95),
    )

private fun JSONObject.fadeWaterFromOcean() {
    val paint = paint()
    val color = paint.opt("fill-color") as? String ?: return
    paint.put("fill-color", earthRamp(Ocean, color))
}

// The dark style's grey labels with black halos turn muddy over the relief.
private fun JSONObject.brightenLabel() {
    val paint = paint()
    val color = paint.opt("text-color") as? String ?: return
    paint.put("text-color", earthRamp(DarkEarthLabel, color))
}

private fun JSONObject.paint(): JSONObject =
    optJSONObject("paint") ?: JSONObject().also { put("paint", it) }

private fun earthRamp(earth: Any, map: Any) = zoomRamp(EarthFadeStart, earth, EarthFadeEnd, map)

private fun zoomRamp(fromZoom: Int, from: Any, toZoom: Int, to: Any) = JSONArray()
    .put("interpolate").put(JSONArray().put("linear")).put(JSONArray().put("zoom"))
    .put(fromZoom).put(from)
    .put(toZoom).put(to)
