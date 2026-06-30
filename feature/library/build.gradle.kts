plugins {
    id("rolla.android.feature")
    id("rolla.android.library.compose")
}

android {
    namespace = "com.rolla.musicplayer.feature.library"
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
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":core:media"))
    implementation(project(":core:permissions"))
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.hilt.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(project(":core:testing"))
}
