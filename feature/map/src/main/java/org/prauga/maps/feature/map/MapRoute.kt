package org.prauga.maps.feature.map

import androidx.compose.runtime.Composable

@Composable
fun MapRoute() {
    LocationPermissionGate {
        MapScreen()
    }
}
