plugins {
    id("rolla.android.library")
    id("rolla.android.library.compose")
    id("rolla.android.hilt")
}

android {
    namespace = "com.rolla.musicplayer.feature.widget"
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:media"))
    implementation(project(":core:designsystem"))
    // BOM + material3 are compile-time requirements of MusicWidgetTheme's ColorScheme mapping:
    // glance-material3 uses compose material3 internally but doesn't export it (api) to consumers.
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.material3)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.hilt.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(project(":core:testing"))
}
