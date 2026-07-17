import com.android.build.api.dsl.ManagedVirtualDevice

plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.rolla.musicplayer.baselineprofile"
    compileSdk = 34
    defaultConfig {
        minSdk = 28
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Macrobenchmark's own error-suppression flag. "EMULATOR" tells it the run is on an
        // emulator, so thermal/low-battery/etc. device checks are relaxed and results are reported
        // as comparative rather than absolute. Unused (harmless) on a physical device.
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
    }
    targetProjectPath = ":app"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    testOptions.managedDevices.devices {
        create<ManagedVirtualDevice>("pixel6Api31") {
            device = "Pixel 6"
            apiLevel = 31
            systemImageSource = "aosp-atd"
        }
    }
}

// This module intentionally declares NO build types of its own: the androidx.baselineprofile
// plugin auto-manages two against :app's "release" build type --
//   - "benchmarkRelease": non-debuggable/debug-signed, what StartupBenchmark/ScrollBenchmark
//     (MacrobenchmarkRule) run against.
//   - "nonMinifiedRelease": a non-obfuscated release used by BaselineProfileGenerator
//     (BaselineProfileRule) so collected profile rules map back to real class/method names.
// (An earlier attempt at manually declaring a "benchmark" build type here -- to mirror :app's --
// collided with the plugin's own auto-generated one and produced duplicate/ambiguous variants;
// removed. :app's separate "benchmark" build type is still used directly for
// `:app:assembleBenchmark` / as the `baselineProfile(project(":baselineprofile"))` consumer.)
//
// CAUTION: any `assemble`/`assembleAndroidTest`-family task in this module can transitively
// trigger the plugin's device-collection flow (booting the managed device / running against a
// connected one) -- there is no purely "compile-only, assemble-safe" task once the plugin is
// applied. Use `compile<Variant>Kotlin` (e.g. `compileBenchmarkReleaseKotlin`) for a
// code-correctness-only check; never run `assemble`/`connected*AndroidTest`/`<device>*AndroidTest`
// here without an intentional device run in mind.
//
// Two ways to actually generate the profile / run the benchmarks (no device attached in this
// environment -- pick one later):
//   - Managed device (default; Gradle provisions/boots an AVD automatically, CI-friendly):
//       ./gradlew :app:generateBaselineProfile
//       ./gradlew :baselineprofile:pixel6Api31BenchmarkReleaseAndroidTest
//   - An already-connected physical device or a manually-started emulator:
//       ./gradlew :app:generateBaselineProfile -Pbaselineprofile.useConnected
//       ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest -Pbaselineprofile.useConnected
baselineProfile {
    managedDevices += "pixel6Api31"
    useConnectedDevices = providers.gradleProperty("baselineprofile.useConnected").isPresent
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
