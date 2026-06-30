plugins {
    id("rolla.android.library")
    id("rolla.android.library.compose")
}

android {
    namespace = "com.rolla.musicplayer.core.ui"
    buildFeatures {
        compose = true
    }
}

composeCompiler {
    stabilityConfigurationFile.set(
        rootProject.layout.projectDirectory.file("config/compose/stability-configuration.conf"),
    )
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.coil.compose)
    implementation(libs.androidx.compose.material.icons.extended)
}
