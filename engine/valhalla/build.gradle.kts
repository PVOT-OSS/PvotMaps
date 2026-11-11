/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    id("maps.android.library")
    id("maps.hilt")
}

android { namespace = "org.prauga.maps.engine.valhalla" }

dependencies {
    api(project(":core:routing"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.valhalla.mobile)
}
