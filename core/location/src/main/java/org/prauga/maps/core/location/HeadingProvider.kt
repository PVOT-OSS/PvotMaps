/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.core.location

import kotlinx.coroutines.flow.Flow

interface HeadingProvider {
    /**
     * Which way the top of the screen points, in degrees clockwise from magnetic north. Empty
     * without a compass.
     */
    fun heading(): Flow<Float>
}
