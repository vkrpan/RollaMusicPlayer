plugins {
    id("rolla.android.application")
    id("rolla.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.rolla.musicplayer"
    defaultConfig {
        applicationId = "com.rolla.musicplayer"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":core:media"))
    implementation(project(":core:permissions"))
    implementation(project(":feature:library"))
    implementation(project(":feature:search"))
    implementation(project(":feature:player"))
    implementation(project(":feature:equalizer"))
    implementation(project(":feature:playlists"))
    implementation(project(":feature:tageditor"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:widget"))

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.coil)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(project(":core:testing"))
}
