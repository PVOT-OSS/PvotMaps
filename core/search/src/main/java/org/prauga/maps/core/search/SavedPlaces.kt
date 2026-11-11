/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.core.search

import kotlinx.coroutines.flow.Flow
import org.prauga.maps.core.model.Place

/** The places the user keeps one of each, such as home. */
enum class SavedSlot {
    Home,
    Work,
}

/** Places saved by the user, kept on the device only. */
interface SavedPlaces {
    val places: Flow<Map<SavedSlot, Place>>

    /** Saves [place] as [slot], replacing what was there. */
    suspend fun save(slot: SavedSlot, place: Place)

    suspend fun remove(slot: SavedSlot)
}
