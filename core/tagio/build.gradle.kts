plugins {
    id("rolla.android.library")
    id("rolla.android.hilt")
}

android {
    namespace = "com.rolla.musicplayer.core.tagio"
}

dependencies {
    implementation(project(":core:model"))
    // Offline, on-device ID3v2 / Vorbis comment / MP4 atom tag read-write. `api`: the shared helpers in TagAccess.kt
    // take and return jaudiotagger types (AudioFile, Tag, FieldKey), so every consumer compiles against them.
    api(libs.jaudiotagger)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":core:testing"))
}
