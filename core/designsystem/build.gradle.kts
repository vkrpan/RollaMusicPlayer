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
    implementation(project(":core:common"))
    // WindowCompat for OneUiBottomSheet's window (fix F1); already on the runtime classpath through material3.
    implementation(libs.androidx.core.ktx)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(project(":core:testing"))
}
