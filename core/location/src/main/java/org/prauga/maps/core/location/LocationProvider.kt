/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.core.location

import kotlinx.coroutines.flow.Flow
import org.prauga.maps.core.model.Coordinates

interface LocationProvider {
    /** Whether any location provider is switched on and allowed. */
    fun isEnabled(): Boolean

    /** The most recent fix any provider still remembers, at once; null if none does. */
    fun lastKnownLocation(): Coordinates?

    /** A fresh fix from the best enabled provider, or null if none could be had. */
    suspend fun currentLocation(): Coordinates?

    /** Fixes as the user moves, while collected. Ends at once if no provider is enabled. */
    fun locationUpdates(): Flow<Coordinates>
}
