plugins {
    id("rolla.android.library")
    id("rolla.android.library.compose")
    id("rolla.android.robolectric")
}

android {
    namespace = "com.rolla.musicplayer.core.ui"
    buildFeatures {
        compose = true
    }
}

// Stability configuration is applied centrally by rolla.android.library.compose.

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    androidTestImplementation(project(":core:model"))
    androidTestImplementation(project(":core:designsystem"))
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
