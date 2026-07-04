import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

class RollaAndroidLibraryComposePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            // Compose compiler plugin in Kotlin 2.0+ handles composeOptions automatically
            extensions.configure<ComposeCompilerGradlePluginExtension> {
                // Marks :core:model classes as stable — see the comment in the file itself.
                stabilityConfigurationFile.set(
                    rootProject.layout.projectDirectory.file("config/compose/stability-configuration.conf"),
                )
            }
        }
    }
}
