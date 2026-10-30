plugins {
    id("maps.android.library")
    id("maps.hilt")
}

android {
    namespace = "org.prauga.maps.core.location"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.core)
}
