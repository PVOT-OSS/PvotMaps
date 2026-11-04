/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

plugins {
    id("maps.android.library")
    id("maps.hilt")
}

android { namespace = "org.prauga.maps.core.location" }

dependencies {
    api(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    api(libs.kotlinx.coroutines.core)
}
