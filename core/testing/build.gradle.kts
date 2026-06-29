plugins {
    id("rolla.android.library")
}

android {
    namespace = "com.rolla.musicplayer.core.testing"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.junit)
    implementation(libs.kotlinx.coroutines.test)
}
