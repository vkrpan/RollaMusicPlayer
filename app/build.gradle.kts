import com.android.build.api.artifact.SingleArtifact
import java.util.Properties

plugins {
    id("rolla.android.application")
    id("rolla.android.hilt")
    id("rolla.android.robolectric")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.androidx.baselineprofile)
}

// ── Release signing (reading side only) ─────────────────────────────────────────────────────
// The keystore + credentials are provisioned locally and NEVER committed: `local.properties` and
// `keystore/*.jks` are gitignored (see .gitignore's "Local configuration" / "Keystore" sections).
// This block only *reads* four well-known keys -- RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD,
// RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD -- first from `local.properties`, falling back to
// same-named environment variables (for CI signing without a checked-in properties file).
// Passwords are read into memory only; never printed/logged. If any of the four is missing, the
// `release` build type is left WITHOUT a signingConfig (an explicit unsigned build + a Gradle
// warning) rather than silently falling back to debug signing -- an unsigned release APK is
// obviously unsigned and safe to spot; a debug-signed one looks legitimate and is the real footgun.
val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) {
            file.inputStream().use(::load)
        }
    }

fun releaseSigningProperty(key: String): String? = (localProperties.getProperty(key) ?: System.getenv(key))?.takeIf(String::isNotBlank)

val releaseStoreFile = releaseSigningProperty("RELEASE_STORE_FILE")
val releaseStorePassword = releaseSigningProperty("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseSigningProperty("RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseSigningProperty("RELEASE_KEY_PASSWORD")
val hasReleaseSigningConfig =
    releaseStoreFile != null && releaseStorePassword != null &&
        releaseKeyAlias != null && releaseKeyPassword != null

if (!hasReleaseSigningConfig) {
    logger.warn("release keystore not configured — building unsigned")
}

android {
    namespace = "com.rolla.musicplayer"
    defaultConfig {
        applicationId = "com.rolla.musicplayer"
    }
    buildFeatures {
        compose = true
    }
    if (hasReleaseSigningConfig) {
        signingConfigs {
            create("release") {
                // rootProject.file: RELEASE_STORE_FILE is documented as repo-root-relative (e.g.
                // "keystore/rolla-release.jks"); plain file(...) would resolve against app/.
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        // Minified + shrunk release build, signed with the release keystore when local.properties
        // (or env vars -- see the signing block above) provide one; unsigned + a build-time warning
        // otherwise. Keep rules live in proguard-rules.pro; see that file's header for the
        // per-library rationale (kotlinx-serialization route classes and jaudiotagger need explicit
        // rules; Media3/Room/Hilt/Glance ship their own consumer rules and are verified post-build
        // instead of duplicated here).
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        // A release-like, non-debuggable build for Macrobenchmark/Baseline Profile generation to
        // drive (see .claude/skills/generate-baseline-profile/SKILL.md). Deliberately keeps debug
        // signing (benchmarking doesn't need/want the release keystore); everything else
        // (minification, shrinking, proguard rules) is inherited from `release` via initWith.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":core:data"))
    implementation(project(":core:media"))
    implementation(project(":core:permissions"))
    implementation(project(":feature:library"))
    implementation(project(":feature:search"))
    implementation(project(":feature:player"))
    implementation(project(":feature:equalizer"))
    implementation(project(":feature:playlists"))
    implementation(project(":feature:tageditor"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:widget"))

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.coil)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.profileinstaller)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(project(":core:testing"))

    // Producer module: generates/bundles the Baseline Profile consumed by `benchmark`/`release`.
    baselineProfile(project(":baselineprofile"))
}

// ── Offline enforcement: the release manifest must never carry INTERNET/ACCESS_NETWORK_STATE ──
// Wired via the Variant API (SingleArtifact.MERGED_MANIFEST) rather than a hardcoded
// build/intermediates path, so it (a) survives AGP output-layout changes across versions and
// (b) is correctly task-dependency-wired -- Gradle infers the manifest-merge task dependency
// straight from the Provider<RegularFile>, no explicit dependsOn needed.
androidComponents {
    // :app's Robolectric tests host composables in the androidx ComponentActivity, which only the debug variant's
    // manifest declares (debugImplementation ui-test-manifest; it must never ship in release). JVM tests compile the
    // same sources in every variant, so they run on debug only rather than failing on release/benchmark.
    beforeVariants(selector().all()) { variantBuilder ->
        if (variantBuilder.buildType != "debug") variantBuilder.enableUnitTest = false
    }
    onVariants(selector().withBuildType("release")) { variant ->
        val mergedManifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        tasks.register("checkReleaseManifestNoInternet") {
            group = "verification"
            description =
                "Fails the build if the release merged manifest declares INTERNET or " +
                "ACCESS_NETWORK_STATE (RollaMusicPlayer is offline-only)."
            inputs.file(mergedManifest)
            val marker = layout.buildDirectory.file("checkReleaseManifestNoInternet/verified.txt")
            outputs.file(marker)
            doLast {
                val manifestText = mergedManifest.get().asFile.readText()
                val forbiddenPermissions =
                    listOf(
                        "android.permission.INTERNET",
                        "android.permission.ACCESS_NETWORK_STATE",
                    )
                val offenders = forbiddenPermissions.filter(manifestText::contains)
                require(offenders.isEmpty()) {
                    "Forbidden network permission(s) present in the release manifest " +
                        "(RollaMusicPlayer must stay 100% offline): $offenders"
                }
                val markerFile = marker.get().asFile
                markerFile.parentFile.mkdirs()
                markerFile.writeText("OK — no INTERNET/ACCESS_NETWORK_STATE in the release manifest\n")
            }
        }
    }
}

// Wire into `:app:check` (and therefore the root `./gradlew check`, which runs the check task of
// every subproject), mirroring how the root-level checkNoNetwork is wired into the root `check`.
tasks.named("check") {
    dependsOn("checkReleaseManifestNoInternet")
}
