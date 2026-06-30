// Top-level build file — plugin declarations + project-wide tooling configuration.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    // detekt is applied per-module via the rolla.static.analysis convention plugin;
    // declaring it here (apply false) puts the plugin jar on the classpath for subprojects.
    alias(libs.plugins.detekt) apply false
    // Spotless is applied at root so it can target all *.kt / *.gradle.kts files at once.
    alias(libs.plugins.spotless)
}

// ── Spotless / ktlint formatting ────────────────────────────────────────────
spotless {
    kotlin {
        target("**/*.kt")
        // Exclude build outputs, Gradle cache, and scaffolding placeholder files.
        // Placeholder.kt files contain only a package declaration (no declarations)
        // which triggers ktlint's no-empty-file rule; they will be replaced by
        // real code by feature agents and will be automatically included then.
        targetExclude("**/build/**", "**/.gradle/**", "**/Placeholder.kt")
        ktlint("1.2.1").editorConfigOverride(
            mapOf("ktlint_standard_function-naming" to "disabled"),
        )
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint("1.2.1")
    }
}

// ── Offline enforcement: forbid network imports everywhere in the source tree ──
val forbiddenImports =
    listOf(
        "retrofit2",
        "okhttp3",
        "java.net.HttpURLConnection",
        "com.google.firebase",
        "io.ktor.client",
    )

tasks.register("checkNoNetwork") {
    group = "verification"
    description = "Fails the build if any network dependency import is detected"
    doLast {
        val offenders =
            fileTree(rootDir) {
                include("**/*.kt")
                exclude("**/build/**", "**/.gradle/**")
            }.flatMap { f ->
                f.readLines().mapIndexedNotNull { i, line ->
                    if (forbiddenImports.any { lib -> line.contains("import $lib") }) {
                        "${f.path}:${i + 1}: $line"
                    } else {
                        null
                    }
                }
            }
        require(offenders.isEmpty()) {
            "Network dependency detected (app is offline-only):\n" + offenders.joinToString("\n")
        }
    }
}

// Wire checkNoNetwork into the root-level check lifecycle task.
// The base plugin is applied explicitly to ensure the check task exists at root
// so `./gradlew check` runs checkNoNetwork in addition to all subproject checks.
apply(plugin = "base")
tasks.named("check") {
    dependsOn("checkNoNetwork")
}
