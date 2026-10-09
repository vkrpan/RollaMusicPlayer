import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * JVM (Robolectric) Compose tests. ui-test-manifest is testImplementation, not debugImplementation, because `check`
 * also runs testReleaseUnitTest, which needs ComponentActivity in the merged manifest.
 */
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
