plugins {
    id("rolla.android.library")
    id("rolla.android.hilt")
}

android {
    namespace = "com.rolla.musicplayer.core.media"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.kotlinx.coroutines.android)
}
