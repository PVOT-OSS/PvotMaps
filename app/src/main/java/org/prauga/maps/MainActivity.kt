package org.prauga.maps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import org.prauga.maps.feature.map.MapRoute
import org.prauga.maps.ui.theme.PvotMapsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PvotMapsTheme {
                MapRoute()
            }
        }
    }
}
