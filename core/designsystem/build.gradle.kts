plugins {
    id("rolla.android.library")
    id("rolla.android.library.compose")
    id("rolla.android.robolectric")
}

android {
    namespace = "com.rolla.musicplayer.core.designsystem"
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
