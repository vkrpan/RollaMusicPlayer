plugins {
    id("rolla.android.library")
    id("rolla.android.library.compose")
    id("rolla.android.robolectric")
}

android {
    namespace = "com.rolla.musicplayer.core.permissions"
    buildFeatures {
        compose = true
    }
}

dependencies {
    // The One UI kit for the prompt (Phase 6b ruling 8): ContentPanel, ArtworkPlaceholder, OneUiPillButton, tokens.
    implementation(project(":core:designsystem"))

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)

    testImplementation(project(":core:testing"))
}
