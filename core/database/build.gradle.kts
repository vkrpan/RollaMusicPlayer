plugins {
    id("rolla.android.library")
    id("rolla.android.room")
    id("rolla.android.hilt")
}

android {
    namespace = "com.rolla.musicplayer.core.database"
}

dependencies {
    implementation(projects.core.model)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.espresso.core)
}
