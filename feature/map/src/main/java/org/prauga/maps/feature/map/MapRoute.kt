/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps.feature.map

import androidx.compose.runtime.Composable

@Composable
fun MapRoute() {
    LocationPermissionGate { MapScreen() }
}
