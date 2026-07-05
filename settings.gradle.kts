pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // JitPack: required for the Android-compatible JAudiotagger fork
        // (com.github.Adonai:jaudiotagger) used by :feature:tageditor. Local, on-device
        // tag parsing library only — see build-tooling-agent for the local-only vetting.
        maven(url = "https://jitpack.io")
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "RollaMusicPlayer"

include(":app")

// Core modules
include(
    ":core:model",
    ":core:common",
    ":core:database",
    ":core:datastore",
    ":core:data",
    ":core:media",
    ":core:designsystem",
    ":core:ui",
    ":core:permissions",
    ":core:testing",
)

// Feature modules
include(
    ":feature:library",
    ":feature:search",
    ":feature:player",
    ":feature:equalizer",
    ":feature:playlists",
    ":feature:tageditor",
    ":feature:settings",
    ":feature:widget",
)

include(":baselineprofile")
