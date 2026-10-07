plugins {
    id("rolla.android.library")
    id("rolla.android.library.compose")
}

android {
    namespace = "com.rolla.musicplayer.core.designsystem"
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests {
            // Robolectric Compose tests need the merged manifest (ComponentActivity from ui-test-manifest).
            isIncludeAndroidResources = true
        }
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

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    testImplementation(composeBom)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    // testImplementation, not debugImplementation: `check` also runs testReleaseUnitTest, which needs ComponentActivity.
    testImplementation(libs.androidx.compose.ui.test.manifest)
}
