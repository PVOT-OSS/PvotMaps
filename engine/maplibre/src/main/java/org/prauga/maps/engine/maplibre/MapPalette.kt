package org.prauga.maps.engine.maplibre

import org.json.JSONArray
import org.json.JSONObject

/** Our colours, laid over the base style's layers. */
internal data class MapPalette(
    val land: String,
    val residential: String,
    val park: String,
    val wood: String,
    val ice: String,
    val sand: String,
    val landmark: String,
    val water: String,
    val aeroway: String,
    val runway: String,
    val building: String,
    val buildingOutline: String,
    val path: String,
    val minorRoad: String,
    val minorCasing: String,
    val secondaryRoad: String,
    val secondaryCasing: String,
    val primaryRoad: String,
    val primaryCasing: String,
    val motorway: String,
    val motorwayCasing: String,
    val rail: String,
    val boundary: String,
    val placeText: String,
    val roadText: String,
    val poiText: String,
    val waterText: String,
    val halo: String,
) {
    companion object {
        // Slate land and quiet greens, with the road hierarchy carried by brightness.
        val Dark = MapPalette(
            land = "#252b34",
            residential = "rgba(255,255,255,0.03)",
            park = "#243a31",
            wood = "#22382d",
            ice = "#2e3640",
            sand = "#33332d",
            landmark = "#2a303a",
            water = "#1b3a5e",
            aeroway = "#2c323c",
            runway = "#3a414d",
            building = "#2f3540",
            buildingOutline = "#363d49",
            path = "#3b4350",
            minorRoad = "#434c5a",
            minorCasing = "#1e232b",
            secondaryRoad = "#5f6a7b",
            secondaryCasing = "#1e232b",
            primaryRoad = "#8a95a6",
            primaryCasing = "#1e232b",
            motorway = "#c2a15e",
            motorwayCasing = "#1e232b",
            rail = "#4b5363",
            boundary = "#6d7689",
            placeText = "#e3e8ef",
            roadText = "#aab3c1",
            poiText = "#9ea8b6",
            waterText = "#79a6d8",
            halo = "#1a1f26",
        )

        // Warm paper land, white streets, cyan water.
        val Light = MapPalette(
            land = "#f1eee8",
            residential = "rgba(0,0,0,0.02)",
            park = "#cde8c0",
            wood = "#c4e2b6",
            ice = "#eef4f6",
            sand = "#f2ead0",
            landmark = "#ebe7e0",
            water = "#9dd3f0",
            aeroway = "#e6e3dd",
            runway = "#f8f7f4",
            building = "#e2ded7",
            buildingOutline = "#d5d0c8",
            path = "#ffffff",
            minorRoad = "#ffffff",
            minorCasing = "#ddd8cf",
            secondaryRoad = "#ffffff",
            secondaryCasing = "#d2ccc2",
            primaryRoad = "#fff4d6",
            primaryCasing = "#e3c98f",
            motorway = "#ffd27a",
            motorwayCasing = "#dda54a",
            rail = "#bcb8b1",
            boundary = "#9a95a3",
            placeText = "#2a2e35",
            roadText = "#5b6170",
            poiText = "#6b7280",
            waterText = "#3a7bb0",
            halo = "#ffffff",
        )
    }
}

internal fun JSONArray.withPalette(palette: MapPalette): JSONArray {
    for (i in 0 until length()) getJSONObject(i).recolour(palette)
    return this
}

private fun JSONObject.recolour(p: MapPalette) {
    val id = getString("id")
    val paint = optJSONObject("paint") ?: JSONObject().also { put("paint", it) }
    when (optString("type")) {
        "background" -> paint.put("background-color", p.land)
        "fill" -> fillColour(id, p)?.let {
            paint.put("fill-color", it)
            if (paint.has("fill-outline-color")) {
                paint.put("fill-outline-color", if (id == "building") p.buildingOutline else it)
            }
        }
        "fill-extrusion" -> paint.put("fill-extrusion-color", p.building)
        "line" -> lineColour(id, optString("source-layer"), p)?.let { paint.put("line-color", it) }
        "symbol" -> {
            val text = when (optString("source-layer")) {
                "place" -> p.placeText
                "transportation_name" -> p.roadText
                "poi", "aerodrome_label" -> p.poiText
                "water_name", "waterway" -> p.waterText
                else -> null
            } ?: return
            paint.put("text-color", text)
            paint.put("text-halo-color", p.halo)
        }
    }
}

private fun fillColour(id: String, p: MapPalette): String? = when (id) {
    "landuse_residential" -> p.residential
    "park", "landcover_grass", "landuse_pitch", "landuse_track", "landuse_cemetery" -> p.park
    "landcover_wood" -> p.wood
    "landcover_ice" -> p.ice
    "landcover_sand" -> p.sand
    "landuse_hospital", "landuse_school" -> p.landmark
    "water" -> p.water
    "aeroway_fill" -> p.aeroway
    "building" -> p.building
    else -> null
}

private fun lineColour(id: String, sourceLayer: String, p: MapPalette): String? = when (sourceLayer) {
    "park" -> p.park
    "waterway" -> p.water
    "aeroway" -> p.runway
    "boundary" -> p.boundary
    "transportation" -> {
        val casing = id.endsWith("_casing")
        when {
            "rail" in id -> p.rail
            "path_pedestrian" in id -> if (casing) p.minorCasing else p.path
            "motorway" in id -> if (casing) p.motorwayCasing else p.motorway
            "trunk_primary" in id -> if (casing) p.primaryCasing else p.primaryRoad
            "secondary_tertiary" in id || "link" in id -> if (casing) p.secondaryCasing else p.secondaryRoad
            else -> if (casing) p.minorCasing else p.minorRoad
        }
    }
    else -> null
}
