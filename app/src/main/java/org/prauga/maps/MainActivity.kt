package org.prauga.maps

import android.os.Bundle
import com.prauga.pvot.designsystem.platform.PvotBaseActivity
import com.prauga.pvot.designsystem.theme.PvotAppTheme
import dagger.hilt.android.AndroidEntryPoint
import org.prauga.maps.feature.map.MapRoute

@AndroidEntryPoint
class MainActivity : PvotBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPvotContent { PvotAppTheme { MapRoute() } }
    }
}
