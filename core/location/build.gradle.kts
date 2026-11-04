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
