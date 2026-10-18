plugins {
    id("rolla.android.library")
}

android {
    namespace = "com.rolla.musicplayer.core.common"
}

dependencies {
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
