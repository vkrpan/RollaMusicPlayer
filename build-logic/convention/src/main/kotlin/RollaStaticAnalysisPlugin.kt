import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** The source sets detekt analyses in every module, and the language directories inside each. */
private val DETEKT_SOURCE_SETS = listOf("main", "test", "androidTest")
private val DETEKT_LANGUAGES = listOf("kotlin", "java")

class RollaStaticAnalysisPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("io.gitlab.arturbosch.detekt")
            extensions.configure<DetektExtension> {
                config.setFrom(rootProject.file("config/detekt/detekt.yml"))
                // detekt's default source is main and test only. androidTest is added so its rules, among them the
                // ForbiddenImport barrier against Material icons (Phase 6b ruling 12), cover instrumented tests too.
                source.setFrom(
                    DETEKT_SOURCE_SETS.flatMap { sourceSet ->
                        DETEKT_LANGUAGES.map { language -> file("src/$sourceSet/$language") }
                    },
                )
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
