import org.gradle.api.Plugin
import org.gradle.api.Project

class RollaAndroidLibraryComposePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            // Compose compiler plugin in Kotlin 2.0+ handles composeOptions automatically
        }
    }
}
