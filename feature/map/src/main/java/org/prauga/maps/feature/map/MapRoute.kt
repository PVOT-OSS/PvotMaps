package org.prauga.maps.feature.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun MapRoute() {
    LocationPermissionGate {
        Box(Modifier.fillMaxSize())
    }
}
