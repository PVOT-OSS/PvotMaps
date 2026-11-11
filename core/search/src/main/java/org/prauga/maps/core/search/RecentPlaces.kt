/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.core.search

import kotlinx.coroutines.flow.Flow
import org.prauga.maps.core.model.Place

/** Places the user picked lately, newest first, kept on the device only. */
interface RecentPlaces {
    val places: Flow<List<Place>>

    /**
     * Puts [place] first, dropping an older entry for the same place and the oldest past the limit.
     */
    suspend fun add(place: Place)
}
