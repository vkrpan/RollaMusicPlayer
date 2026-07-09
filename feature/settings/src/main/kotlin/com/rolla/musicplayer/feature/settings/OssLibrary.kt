package com.rolla.musicplayer.feature.settings

/** One open-source dependency shown on the Licenses screen. */
data class OssLibrary(
    val name: String,
    val project: String,
    val license: String,
)

/**
 * The app's runtime open-source dependencies, curated by hand from `gradle/libs.versions.toml`
 * (runtime only -- test-only libraries like JUnit/MockK/Turbine don't ship in the APK and are
 * deliberately not listed). Fully local: this list is compiled into the app, never fetched.
 *
 * MAINTENANCE NOTE: when a runtime dependency is added to or removed from the version catalog,
 * update this list in the same change. Generating it at build time (e.g. via a license-report
 * Gradle plugin wired by build-tooling-agent) is a Phase 7 option; a hand-curated list keeps this
 * phase free of new build plugins.
 */
internal val OSS_LIBRARIES = listOf(
    OssLibrary("Kotlin Standard Library", "JetBrains / Kotlin", "Apache-2.0"),
    OssLibrary("kotlinx.coroutines", "JetBrains / Kotlin", "Apache-2.0"),
    OssLibrary("kotlinx.serialization", "JetBrains / Kotlin", "Apache-2.0"),
    OssLibrary("AndroidX Core, Lifecycle & Activity", "Android Open Source Project", "Apache-2.0"),
    OssLibrary("Jetpack Compose & Material 3", "Android Open Source Project", "Apache-2.0"),
    OssLibrary("AndroidX Navigation Compose", "Android Open Source Project", "Apache-2.0"),
    OssLibrary("AndroidX Media3 (ExoPlayer)", "Android Open Source Project", "Apache-2.0"),
    OssLibrary("AndroidX Room", "Android Open Source Project", "Apache-2.0"),
    OssLibrary("AndroidX DataStore", "Android Open Source Project", "Apache-2.0"),
    OssLibrary("AndroidX Glance", "Android Open Source Project", "Apache-2.0"),
    OssLibrary("Dagger Hilt", "Google", "Apache-2.0"),
    OssLibrary("Coil", "Coil Contributors", "Apache-2.0"),
    OssLibrary("jaudiotagger (Android fork)", "Paul Taylor / Adonai", "LGPL-2.1"),
)
