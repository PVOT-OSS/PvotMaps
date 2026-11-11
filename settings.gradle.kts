/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Pvot Maps"

include(":app")

include(":core:location")

include(":core:model")

include(":core:navigation")

include(":core:routing")

include(":core:search")

include(":core:settings")

include(":data:search")

include(":engine:maplibre")

include(":engine:valhalla")

include(":feature:map")

// PvotLib builds from the submodule with its own build-logic and version catalog;
// com.prauga.pvot:design-system resolves to its :design-system project.
includeBuild("PvotLib")
