plugins {
    id("rolla.android.library")
    id("rolla.android.room")
    id("rolla.android.hilt")
}

android {
    namespace = "com.rolla.musicplayer.core.database"
}

dependencies {
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
}
