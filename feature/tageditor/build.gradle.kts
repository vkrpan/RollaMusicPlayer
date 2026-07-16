plugins {
    id("rolla.android.feature")
    id("rolla.android.library.compose")
}

android {
    namespace = "com.rolla.musicplayer.feature.tageditor"
    buildFeatures {
        compose = true
    }
    testOptions {
        // Robolectric (MediaWriteRequesterTest) needs the merged manifest/resources to resolve
        // its per-SDK android-all environment; scoped to this module only, not the convention
        // plugins, since no other module runs Robolectric tests.
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":core:media"))
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.coil.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Offline, on-device ID3v2 / Vorbis comment / MP4 atom tag read-write.
    // No network stack: pulls in only okio (local I/O) at runtime — verified via
    // `:feature:tageditor:dependencies`. See build-tooling-agent report.
    implementation(libs.jaudiotagger)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(project(":core:testing"))
}
