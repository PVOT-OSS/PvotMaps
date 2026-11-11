/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.data.search

import org.json.JSONArray
import org.json.JSONObject
import org.prauga.maps.core.model.Coordinates
import org.prauga.maps.core.model.Place
import org.prauga.maps.core.model.PlaceKind

// Whole places are kept, not their row ids, which change whenever the search data is rebuilt.
internal object PlaceJson {
    fun encode(place: Place): JSONObject =
        JSONObject()
            .put("name", place.name)
            .put("localName", place.localName)
            .put("category", place.category)
            .put("kind", place.kind.name)
            .put("lat", place.coordinates.latitude)
            .put("lon", place.coordinates.longitude)
            .put("area", place.area)
            .put("street", place.street)
            .put("houseNumber", place.houseNumber)

    fun decode(json: JSONObject, id: Long): Place =
        Place(
            id = id,
            name = json.getString("name"),
            localName = json.optStringOrNull("localName"),
            category = json.getString("category"),
            kind =
                PlaceKind.entries.firstOrNull { it.name == json.optString("kind") }
                    ?: PlaceKind.Other,
            coordinates = Coordinates(json.getDouble("lat"), json.getDouble("lon")),
            area = json.optStringOrNull("area"),
            street = json.optStringOrNull("street"),
            houseNumber = json.optStringOrNull("houseNumber"),
        )

    fun encodeList(places: List<Place>): String = JSONArray(places.map(::encode)).toString()

    // A list that can't be read is dropped rather than failing the screen.
    fun decodeList(text: String, firstId: Long): List<Place> =
        runCatching {
                val array = JSONArray(text)
                List(array.length()) { index ->
                    decode(array.getJSONObject(index), firstId - index)
                }
            }
            .getOrDefault(emptyList())

    private fun JSONObject.optStringOrNull(name: String): String? =
        if (isNull(name)) null else optString(name)
}
