/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.core.model

/**
 * Where the user is and, when the phone has a compass, which way it points (degrees from north).
 */
data class UserPosition(val coordinates: Coordinates, val heading: Float? = null)
