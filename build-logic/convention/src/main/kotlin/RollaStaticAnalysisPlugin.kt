import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class RollaStaticAnalysisPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("io.gitlab.arturbosch.detekt")
            extensions.configure<DetektExtension> {
                config.setFrom(rootProject.file("config/detekt/detekt.yml"))
                buildUponDefaultConfig = true
                parallel = true
                autoCorrect = false
            }
            // Wire detekt into each module's check lifecycle task so that
            // `./gradlew check` covers static analysis in addition to tests.
            tasks.matching { it.name == "check" }.configureEach {
                dependsOn("detekt")
            }
        }
    }
}
