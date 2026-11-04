import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** The source sets detekt analyses in every module, and the language directories inside each. */
private val DETEKT_SOURCE_SETS = listOf("main", "test", "androidTest")
private val DETEKT_LANGUAGES = listOf("kotlin", "java")

/**
 * Any reference to the Material icons package, whitespace and line breaks allowed between the parts: an import, or a
 * fully qualified name, which detekt's ForbiddenImport cannot see (Phase 7 Task 8, 6b T6 M2).
 */
private val MATERIAL_ICONS_REFERENCE = Regex("""\bandroidx\s*\.\s*compose\s*\.\s*material\s*\.\s*icons\b""")

/** The 1-based line of each reference to the Material icons package in [text]. */
internal fun materialIconReferenceLines(text: String): List<Int> =
    MATERIAL_ICONS_REFERENCE.findAll(text).map { match -> text.substring(0, match.range.first).count { it == '\n' } + 1 }
        .toList()

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
            // Icons come only from RollaIcons (spec §5.5). ForbiddenImport (config/detekt/detekt.yml) rejects the import;
            // this scan also rejects a fully qualified reference, and runs whenever detekt does.
            val sourceRoots = DETEKT_SOURCE_SETS.flatMap { sourceSet ->
                DETEKT_LANGUAGES.map { language -> file("src/$sourceSet/$language") }
            }
            val iconScan = tasks.register("checkNoMaterialIcons") {
                group = "verification"
                description = "Fails on any reference to androidx.compose.material.icons; icons come from RollaIcons."
                val sources = files(sourceRoots).asFileTree.matching { include("**/*.kt", "**/*.java") }
                inputs.files(sources)
                val marker = layout.buildDirectory.file("checkNoMaterialIcons/verified.txt")
                outputs.file(marker)
                doLast {
                    val found = sources.files.sorted().flatMap { source ->
                        materialIconReferenceLines(source.readText()).map { line -> "${source.path}:$line" }
                    }
                    if (found.isNotEmpty()) {
                        throw GradleException(
                            "Material icons referenced (use RollaIcons, spec §5.5):\n" + found.joinToString("\n"),
                        )
                    }
                    marker.get().asFile.apply { parentFile.mkdirs() }.writeText("no Material icon references\n")
                }
            }
            tasks.matching { it.name == "detekt" }.configureEach { dependsOn(iconScan) }
            // Wire detekt into each module's check lifecycle task so that
            // `./gradlew check` covers static analysis in addition to tests.
            tasks.matching { it.name == "check" }.configureEach {
                dependsOn("detekt")
            }
        }
    }
}
