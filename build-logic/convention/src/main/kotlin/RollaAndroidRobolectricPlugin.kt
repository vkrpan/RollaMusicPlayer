import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType

/**
 * JVM (Robolectric) Compose tests. ui-test-manifest is testImplementation, not debugImplementation, because `check`
 * also runs testReleaseUnitTest, which needs ComponentActivity in the merged manifest.
 */
/**
 * Robolectric and mockk's agent append to the boot class path, so the JVM's class-data sharing prints "Sharing is only
 * supported for boot loader classes" in test logs; turning CDS off for every test task removes that noise (6b T5 M1).
 * Applied by the base Android library/application and JVM library plugins, so modules without Robolectric get it too.
 */
internal fun Project.disableTestClassDataSharing() {
    tasks.withType<Test>().configureEach { jvmArgs("-Xshare:off") }
}

class RollaAndroidRobolectricPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // Everything happens inside withPlugin, so the plugin works in any order relative to AGP and is a
            // no-op on non-Android modules.
            pluginManager.withPlugin("com.android.library") {
                extensions.configure<LibraryExtension> { testOptions.unitTests.isIncludeAndroidResources = true }
                addRobolectricTestDependencies()
            }
            pluginManager.withPlugin("com.android.application") {
                extensions.configure<ApplicationExtension> { testOptions.unitTests.isIncludeAndroidResources = true }
                addRobolectricTestDependencies()
            }
        }
    }

    private fun Project.addRobolectricTestDependencies() {
        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        dependencies {
            add("testImplementation", libs.findLibrary("junit").get())
            add("testImplementation", libs.findLibrary("robolectric").get())
            add("testImplementation", libs.findLibrary("androidx-junit").get())
            add("testImplementation", platform(libs.findLibrary("androidx-compose-bom").get()))
            add("testImplementation", libs.findLibrary("androidx-compose-ui-test-junit4").get())
            add("testImplementation", libs.findLibrary("androidx-compose-ui-test-manifest").get())
        }
    }
}
