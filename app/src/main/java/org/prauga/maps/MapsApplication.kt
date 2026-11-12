/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.maps

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import org.prauga.maps.core.regions.RegionRepository

@HiltAndroidApp
class MapsApplication : Application() {
    // Created at start so it tidies up after earlier versions' downloads before search and
    // routing first read the data, not only once the offline regions are opened.
    @Inject lateinit var regions: RegionRepository
}
