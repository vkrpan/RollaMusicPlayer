plugins {
    id("rolla.android.library")
}

android {
    namespace = "com.rolla.musicplayer.core.testing"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    api(project(":core:database"))
    api(project(":core:datastore"))
    api(project(":core:data"))
    implementation(libs.junit)
    implementation(libs.kotlinx.coroutines.test)
    // Pixel-test helpers (PixelCapture.kt). Every consumer takes :core:testing as testImplementation only, so these
    // never reach a production or androidTest classpath.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui.test.junit4)
    implementation(libs.robolectric)
}
