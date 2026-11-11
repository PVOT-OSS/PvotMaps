/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    id("maps.android.library")
    id("maps.hilt")
}

android {
    namespace = "org.prauga.maps.data.regions"
    buildFeatures { buildConfig = true }
    defaultConfig {
        // Where regions.json and the region files are hosted (tools/regions/package.py writes
        // them). Empty means this build can't download regions.
        val url = providers.gradleProperty("pvotMapsRegionsUrl").getOrElse("")
        buildConfigField("String", "REGIONS_URL", "\"$url\"")
    }
}

dependencies {
    api(project(":core:regions"))
    implementation(libs.kotlinx.coroutines.core)
}
