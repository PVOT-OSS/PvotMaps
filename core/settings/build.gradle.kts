plugins {
    id("maps.android.library")
    id("maps.hilt")
}

android { namespace = "org.prauga.maps.core.settings" }

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.datastore.preferences)
}
