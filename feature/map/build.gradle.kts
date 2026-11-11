/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    id("maps.android.library")
    id("maps.android.compose")
    id("maps.hilt")
}

android { namespace = "org.prauga.maps.feature.map" }

dependencies {
    implementation(project(":core:location"))
    implementation(project(":core:regions"))
    implementation(project(":core:navigation"))
    implementation(project(":core:routing"))
    implementation(project(":core:search"))
    implementation(project(":core:settings"))
    implementation(project(":engine:maplibre"))
    implementation(libs.pvot.design.system)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
