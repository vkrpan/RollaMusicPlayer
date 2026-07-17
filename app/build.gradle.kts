plugins {
    id("rolla.android.application")
    id("rolla.android.hilt")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.rolla.musicplayer"
    defaultConfig {
        applicationId = "com.rolla.musicplayer"
    }
    buildFeatures {
        compose = true
    }
    buildTypes {
        // A release-like, non-debuggable build for Macrobenchmark/Baseline Profile generation to
        // drive (see .claude/skills/generate-baseline-profile/SKILL.md). NOTE: `release` itself is
        // not yet minified/signed here (that lands with the separate release-config work), so
        // `benchmark` currently inherits that too -- regenerate the profile once minification is
        // turned on for release, since R8 output can shift which methods/classes are hot.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
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
    implementation(libs.androidx.profileinstaller)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(project(":core:testing"))

    // Producer module: generates/bundles the Baseline Profile consumed by `benchmark`/`release`.
    baselineProfile(project(":baselineprofile"))
}
