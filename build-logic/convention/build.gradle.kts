plugins {
    `kotlin-dsl`
}

group = "com.rolla.musicplayer.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    compileOnly(libs.plugins.android.application.toDep())
    compileOnly(libs.plugins.android.library.toDep())
    compileOnly(libs.plugins.kotlin.android.toDep())
    compileOnly(libs.plugins.kotlin.jvm.toDep())
    compileOnly(libs.plugins.kotlin.compose.toDep())
    compileOnly(libs.plugins.hilt.toDep())
    compileOnly(libs.plugins.ksp.toDep())
    compileOnly(libs.plugins.room.toDep())
    // Detekt plugin marker (for consistency) + implementation jar (needed to
    // reference DetektExtension in RollaStaticAnalysisPlugin at compile time).
    compileOnly(libs.plugins.detekt.toDep())
    compileOnly("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.6")
}

// Helper to convert a PluginDependency from version catalog to a module dependency
fun Provider<PluginDependency>.toDep() =
    map {
        "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}"
    }

gradlePlugin {
    plugins {
        register("rollaAndroidApplication") {
            id = "rolla.android.application"
            implementationClass = "RollaAndroidApplicationPlugin"
        }
        register("rollaAndroidLibrary") {
            id = "rolla.android.library"
            implementationClass = "RollaAndroidLibraryPlugin"
        }
        register("rollaAndroidLibraryCompose") {
            id = "rolla.android.library.compose"
            implementationClass = "RollaAndroidLibraryComposePlugin"
        }
        register("rollaAndroidFeature") {
            id = "rolla.android.feature"
            implementationClass = "RollaAndroidFeaturePlugin"
        }
        register("rollaAndroidHilt") {
            id = "rolla.android.hilt"
            implementationClass = "RollaAndroidHiltPlugin"
        }
        register("rollaAndroidRoom") {
            id = "rolla.android.room"
            implementationClass = "RollaAndroidRoomPlugin"
        }
        register("rollaAndroidRobolectric") {
            id = "rolla.android.robolectric"
            implementationClass = "RollaAndroidRobolectricPlugin"
        }
        register("rollaJvmLibrary") {
            id = "rolla.jvm.library"
            implementationClass = "RollaJvmLibraryPlugin"
        }
        register("rollaStaticAnalysis") {
            id = "rolla.static.analysis"
            implementationClass = "RollaStaticAnalysisPlugin"
        }
    }
}
