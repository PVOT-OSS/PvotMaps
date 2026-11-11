/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

plugins { id("maps.android.library") }

android { namespace = "org.prauga.maps.core.regions" }

dependencies { api(libs.kotlinx.coroutines.core) }
