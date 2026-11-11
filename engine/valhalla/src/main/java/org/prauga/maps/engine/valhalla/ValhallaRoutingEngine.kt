/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.engine.valhalla

import android.content.Context
import com.valhalla.valhalla.Valhalla
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.prauga.maps.core.model.Coordinates
import org.prauga.maps.core.model.Maneuver
import org.prauga.maps.core.model.Route
import org.prauga.maps.core.model.TravelMode
import org.prauga.maps.core.routing.RouteResult
import org.prauga.maps.core.routing.RoutingEngine

// What tools/routing/build_tiles.sh writes, installed per region on the device.
private const val RoutingDir = "routing"
private const val Tiles = "tiles.tar"
private const val BuildConfig = "valhalla.json"
private const val DeviceConfig = "valhalla.device.json"
private const val MetersPerKilometer = 1000.0

@Singleton
internal class ValhallaRoutingEngine
@Inject
constructor(@param:ApplicationContext private val context: Context) : RoutingEngine {

    private val mutex = Mutex()
    private var valhalla: Valhalla? = null

    override suspend fun route(from: Coordinates, to: Coordinates, mode: TravelMode): RouteResult =
        withContext(Dispatchers.IO) {
            val engine = engine() ?: return@withContext RouteResult.NoData
            val response =
                try {
                    engine.routeRaw(request(from, to, mode).toString())
                } catch (e: Exception) {
                    return@withContext RouteResult.NotFound(e.message ?: e.javaClass.simpleName)
                }
            parse(JSONObject(response), mode)
        }

    private suspend fun engine(): Valhalla? =
        mutex.withLock {
            valhalla?.let {
                return it
            }
            val dir = File(context.filesDir, RoutingDir)
            val tiles = File(dir, Tiles)
            val buildConfig = File(dir, BuildConfig)
            if (!tiles.isFile || !buildConfig.isFile) return null
            val deviceConfig = File(dir, DeviceConfig)
            deviceConfig.writeText(deviceConfig(buildConfig, tiles, dir).toString())
            Valhalla(deviceConfig.path).also { valhalla = it }
        }

    // The build's config points at the build machine's paths; aim it at the tiles on this device
    // and drop the inputs only the build needed.
    private fun deviceConfig(buildConfig: File, tiles: File, dir: File): JSONObject {
        val config = JSONObject(buildConfig.readText())
        config.getJSONObject("mjolnir").apply {
            put("tile_extract", tiles.path)
            put("tile_dir", File(dir, "tiles").path)
            put("traffic_extract", "")
            put("admin", "")
            put("timezone", "")
        }
        return config
    }

    private fun request(from: Coordinates, to: Coordinates, mode: TravelMode) =
        JSONObject()
            .put(
                "locations",
                JSONArray()
                    .put(JSONObject().put("lat", from.latitude).put("lon", from.longitude))
                    .put(JSONObject().put("lat", to.latitude).put("lon", to.longitude)),
            )
            .put("costing", costing(mode))
            .put("directions_options", JSONObject().put("units", "kilometers"))

    private fun costing(mode: TravelMode) =
        when (mode) {
            TravelMode.Car -> "auto"
            TravelMode.Bike -> "bicycle"
            TravelMode.Walk -> "pedestrian"
        }

    private fun parse(json: JSONObject, mode: TravelMode): RouteResult {
        val trip = json.optJSONObject("trip") ?: return RouteResult.NotFound(json.toString())
        val legs = trip.getJSONArray("legs")
        val shape = mutableListOf<Coordinates>()
        val maneuvers = mutableListOf<Maneuver>()
        for (i in 0 until legs.length()) {
            val leg = legs.getJSONObject(i)
            val offset = shape.size
            shape += decodePolyline6(leg.getString("shape"))
            val legManeuvers = leg.getJSONArray("maneuvers")
            for (j in 0 until legManeuvers.length()) {
                val m = legManeuvers.getJSONObject(j)
                maneuvers +=
                    Maneuver(
                        instruction = m.getString("instruction"),
                        verbalInstruction =
                            m.optString("verbal_pre_transition_instruction").ifEmpty { null },
                        distanceMeters = m.optDouble("length", 0.0) * MetersPerKilometer,
                        durationSeconds = m.optDouble("time", 0.0),
                        shapeIndex = offset + m.optInt("begin_shape_index", 0),
                    )
            }
        }
        val summary = trip.getJSONObject("summary")
        return RouteResult.Found(
            Route(
                mode = mode,
                shape = shape,
                distanceMeters = summary.getDouble("length") * MetersPerKilometer,
                durationSeconds = summary.getDouble("time"),
                maneuvers = maneuvers,
            )
        )
    }
}
