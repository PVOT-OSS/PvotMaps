plugins {
    id("maps.android.library")
    id("maps.android.compose")
}

android {
    namespace = "org.prauga.maps.engine.maplibre"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.maplibre.android)
}
