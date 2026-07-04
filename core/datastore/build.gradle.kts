plugins {
    id("rolla.android.library")
    id("rolla.android.hilt")
}

android {
    namespace = "com.rolla.musicplayer.core.datastore"
}

dependencies {
    implementation(libs.datastore.preferences)

    testImplementation(libs.junit)
}
