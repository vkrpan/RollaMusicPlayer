plugins {
    id("rolla.android.library")
    id("rolla.android.library.compose")
    id("rolla.android.hilt")
}

android {
    namespace = "com.rolla.musicplayer.feature.widget"
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.hilt.navigation.compose)
}
