/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

plugins { id("maps.android.library") }

android { namespace = "org.prauga.maps.core.navigation" }

dependencies { api(project(":core:model")) }
