plugins {
    id("rolla.android.library")
}

android {
    namespace = "com.rolla.musicplayer.core.testing"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    api(project(":core:database"))
    api(project(":core:datastore"))
    api(project(":core:data"))
    implementation(libs.junit)
    implementation(libs.kotlinx.coroutines.test)
}
