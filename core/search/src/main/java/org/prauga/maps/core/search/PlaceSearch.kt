/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.core.search

import org.prauga.maps.core.model.Coordinates
import org.prauga.maps.core.model.Place

/** Finds places by name on the device, without sending what was typed anywhere. */
interface PlaceSearch {
    /** Whether search data for a region is installed. */
    suspend fun isAvailable(): Boolean

    /**
     * Places whose names start with every word of [query], best matches first and, among equals,
     * nearest to [near]. Empty when nothing matches or no search data is installed.
     */
    suspend fun search(query: String, near: Coordinates?, limit: Int = DefaultLimit): List<Place>

    companion object {
        const val DefaultLimit = 30
    }
}
