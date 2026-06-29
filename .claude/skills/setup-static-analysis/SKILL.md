---
name: setup-static-analysis
description: "Step-by-step workflow for wiring detekt + ktlint (via Spotless) into RollaMusicPlayer so the kotlin-style and offline rules are actually enforced — Gradle config, rule baselines, a custom 'no network dependency' check, a pre-commit hook, and CI integration."
---

# Skill: Setup Static Analysis

# Overview
A workflow for enforcing the project's coding standards automatically with detekt (static analysis) and ktlint (formatting, via Spotless). The `kotlin-style` rule already references these tools but nothing runs them; this skill wires them into Gradle, adds config and a baseline, adds a project-specific check that flags accidental network dependencies (protecting the offline architecture), and hooks them into pre-commit and CI.

## When to Use
- Setting up the project's quality gates
- Enforcing `kotlin-style` / `compose-conventions` automatically
- Catching accidental network imports before they land
- Adding lint/format checks to CI

## Prerequisites
- Gradle (Kotlin DSL) with a version catalog (`gradle/libs.versions.toml`) — coordinate with build-tooling-agent
- The `kotlin-style` and `compose-conventions` rules as the source of truth for standards

## Workflow Steps

### Step 1: Add the Plugins
**Goal**: detekt + Spotless (ktlint) available to all modules

**Implementation**:
```toml
# gradle/libs.versions.toml
[versions]
detekt = "1.23.6"
spotless = "6.25.0"

[plugins]
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
spotless = { id = "com.diffplug.spotless", version.ref = "spotless" }
```

```kotlin
// root build.gradle.kts
plugins {
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless)
}
```

### Step 2: Configure Spotless (ktlint formatting)
**Goal**: Consistent formatting, auto-fixable

**Implementation**:
```kotlin
// root build.gradle.kts
spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**")
        ktlint("1.2.1").editorConfigOverride(
            mapOf("ktlint_standard_function-naming" to "disabled") // Composables are PascalCase
        )
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        ktlint("1.2.1")
    }
}
```
> The Composable-naming exception matters: ktlint's default function-naming rule flags `@Composable fun SongList()`. Disable that rule (or use the compose ktlint ruleset) to match `compose-conventions`.

### Step 3: Configure detekt
**Goal**: Static analysis tuned to the project

**Implementation**:
```kotlin
// build.gradle.kts (apply in each module or via a convention plugin)
detekt {
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    baseline = file("detekt-baseline.xml")
    buildUponDefaultConfig = true
    parallel = true
}
```
```yaml
# config/detekt/detekt.yml (excerpt — extend the defaults)
complexity:
  LongMethod:
    threshold: 30          # matches kotlin-style "functions < 30 lines"
  TooManyFunctions:
    active: true
style:
  MagicNumber:
    active: true
    ignorePropertyDeclaration: true
naming:
  FunctionNaming:
    ignoreAnnotated: ['Composable']   # allow PascalCase composables
```

### Step 4: Add a Project-Specific "No Network" Check
**Goal**: Fail the build if a networking dependency or import sneaks in (protects the offline architecture)

**Implementation** (simplest: a Gradle verification task; optionally a custom detekt rule):
```kotlin
// build.gradle.kts (root or convention plugin)
val forbiddenImports = listOf(
    "retrofit2", "okhttp3", "java.net.HttpURLConnection",
    "com.google.firebase", "io.ktor.client"
)

tasks.register("checkNoNetwork") {
    group = "verification"
    doLast {
        val offenders = fileTree("src") { include("**/*.kt") }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, line ->
                if (forbiddenImports.any { line.contains("import $it") }) "${f.path}:${i + 1}: $line" else null
            } }
        require(offenders.isEmpty()) {
            "Network dependency detected (app is offline-only):\n" + offenders.joinToString("\n")
        }
    }
}
tasks.named("check") { dependsOn("checkNoNetwork") }
```
> This makes the `code-reviewer` agent's #1 CRITICAL rule enforceable at build time, not just on review.

### Step 5: Wire Gradle Tasks
**Goal**: Simple commands developers and CI run

**Implementation**:
```bash
./gradlew spotlessApply      # auto-format
./gradlew spotlessCheck      # verify formatting
./gradlew detekt             # static analysis
./gradlew check              # runs detekt + spotlessCheck + checkNoNetwork + tests
```
Generate the detekt baseline once to grandfather existing issues:
```bash
./gradlew detektBaseline
```

### Step 6: Add a Pre-Commit Hook
**Goal**: Catch issues before they're committed

**Implementation**:
```bash
# config/git-hooks/pre-commit
#!/bin/sh
./gradlew spotlessCheck detekt checkNoNetwork --daemon || {
  echo "Static analysis failed. Run './gradlew spotlessApply' and fix detekt issues."
  exit 1
}
```
Install it (document in README or a setup task):
```bash
git config core.hooksPath config/git-hooks
chmod +x config/git-hooks/pre-commit
```

### Step 7: Add to CI
**Goal**: Enforce on every PR (coordinate with build-tooling-agent / setup-ci)

**Implementation**:
```yaml
# .github/workflows/ci.yml (excerpt)
- name: Static analysis
  run: ./gradlew spotlessCheck detekt checkNoNetwork
```

### Step 8: Verify
**Checklist**:
- [ ] `spotlessApply` formats; `spotlessCheck` passes clean
- [ ] `detekt` runs with the project config and baseline
- [ ] Composable PascalCase names are not flagged
- [ ] `checkNoNetwork` fails the build on a Retrofit/OkHttp/Firebase import
- [ ] `./gradlew check` runs all of them
- [ ] Pre-commit hook blocks bad commits
- [ ] CI runs the checks on PRs
- [ ] Thresholds align with `kotlin-style` (e.g. method length)

## Related Files
- `gradle/libs.versions.toml` — plugin versions
- `config/detekt/detekt.yml` — detekt config
- `detekt-baseline.xml` — grandfathered issues
- `config/git-hooks/pre-commit` — local hook
- `.github/workflows/ci.yml` — CI integration

## Notes
- Spotless wraps ktlint and adds auto-fix; you can use raw ktlint instead, but Spotless is simpler to manage across modules.
- Put detekt/Spotless config in a convention plugin (build-tooling-agent) so every module inherits it without duplication.
- The `checkNoNetwork` task is intentionally simple and fast; a custom detekt rule is a more robust alternative if you want IDE integration.
- Keep the baseline small — fix issues rather than letting the baseline grow.

## Common Pitfalls
- ❌ Not excluding `build/` — analysis chokes on generated code.
- ❌ Leaving FunctionNaming on for Composables — floods reports with false positives.
- ❌ A giant detekt baseline used to silence everything — defeats the purpose.
- ❌ Configuring per-module by copy-paste instead of a convention plugin — drift over time.
