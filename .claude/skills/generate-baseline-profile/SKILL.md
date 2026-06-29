---
name: generate-baseline-profile
description: "Step-by-step workflow for adding Macrobenchmark + Baseline Profiles to RollaMusicPlayer to cut cold-start time and library-scroll jank. Sets up the baselineprofile module, a generator, startup/scroll benchmarks, and the release wiring. Runs locally on a device/emulator — no network."
---

# Skill: Generate Baseline Profile

## Overview
A workflow for generating and shipping a Baseline Profile so critical code paths (app startup, first library render, now-playing) are AOT-compiled on install, plus a Macrobenchmark setup to measure the improvement. This is the highest-leverage runtime performance work for the app: it reduces cold-start time and removes first-scroll jank in the song list with no UI changes. Everything runs on a local device/emulator — no network involved.

## When to Use
- Before release, to ship a Baseline Profile in the APK/AAB
- When measuring/optimizing cold start or library-scroll jank
- After major UI changes that alter the hot startup path
- Setting up performance regression checks in CI

## Prerequisites
- AGP/Gradle with the baseline profile and benchmark plugins available
- A physical device or an AOSP/Google-APIs emulator image (rooted-equivalent for benchmarking)
- App is buildable in a `release`-like benchmark build type (minified, debuggable=false)
- `androidx.profileinstaller` dependency in the app module

## Workflow Steps

### Step 1: Add the Benchmark Build Type
**Goal**: A release-like, profileable build the benchmark drives

**Implementation**:
```kotlin
// app/build.gradle.kts
android {
    buildTypes {
        create("benchmark") {
            initWith(buildTypes.getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            // Keep minification ON so the profile reflects the shipped app
        }
    }
}

dependencies {
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
}
```

### Step 2: Create the Baseline Profile Module
**Goal**: A `com.android.test` module that generates the profile

**Implementation**:
```kotlin
// settings.gradle.kts → include(":baselineprofile")

// baselineprofile/build.gradle.kts
plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.rolla.musicplayer.baselineprofile"
    compileSdk = 34
    defaultConfig {
        minSdk = 28          // Baseline Profiles apply on 28+
        targetSdk = 34
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    targetProjectPath = ":app"
}

dependencies {
    implementation("androidx.test.ext:junit:1.1.5")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.2.4")
}

// app/build.gradle.kts → apply the consumer plugin
plugins {
    id("androidx.baselineprofile")
}
dependencies {
    baselineProfile(project(":baselineprofile"))
}
```

### Step 3: Write the Profile Generator
**Goal**: Drive the critical user journey so the profile captures it

**Implementation**:
```kotlin
// baselineprofile/src/main/java/.../StartupBaselineProfileGenerator.kt
@RunWith(AndroidJUnit4::class)
class StartupBaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = "com.rolla.musicplayer",
        includeInStartupProfile = true
    ) {
        pressHome()
        startActivityAndWait()

        // Critical journey: wait for the library list, then scroll it
        device.wait(Until.hasObject(By.res("song_list")), 5_000)
        val list = device.findObject(By.res("song_list"))
        list?.setGestureMargin(device.displayWidth / 5)
        repeat(3) {
            list?.fling(Direction.DOWN)
            device.waitForIdle()
        }
    }
}
```
> Add `Modifier.testTag("song_list")` to the library `LazyColumn` (coordinate with ui-builder) so UiAutomator can find it.

### Step 4: Add a Macrobenchmark to Measure
**Goal**: Quantify startup and scroll before/after

**Implementation**:
```kotlin
// baselineprofile/src/main/java/.../StartupBenchmark.kt
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupNoCompilation() = startup(CompilationMode.None())

    @Test
    fun startupBaselineProfile() = startup(CompilationMode.Partial())

    private fun startup(mode: CompilationMode) = benchmarkRule.measureRepeated(
        packageName = "com.rolla.musicplayer",
        metrics = listOf(StartupTimingMetric()),
        compilationMode = mode,
        iterations = 10,
        startupMode = StartupMode.COLD
    ) {
        pressHome()
        startActivityAndWait()
    }
}

// Optional: FrameTimingMetric for scroll jank on the library list
```

### Step 5: Generate and Verify
**Goal**: Produce the profile and confirm the win

**Actions**:
1. Generate the profile (writes to `app/src/main/baseline-prof.txt` / `baselineProfiles/`):
```bash
./gradlew :app:generateBaselineProfile
```
2. Run the benchmark both ways and compare `timeToInitialDisplayMs`:
```bash
./gradlew :baselineprofile:connectedBenchmarkAndroidTest
```
3. Confirm `CompilationMode.Partial()` (with profile) beats `None()` — typically 15–40% faster cold start.
4. Build the release APK/AAB and confirm the profile is bundled (ProfileInstaller logs `COMPILE` on first run).

### Step 6: Verify
**Checklist**:
- [ ] `:baselineprofile` module builds and runs on device/emulator
- [ ] Generator exercises startup + library scroll (and now-playing if hot)
- [ ] `generateBaselineProfile` produces a non-empty profile
- [ ] Benchmark shows measurable startup improvement with the profile
- [ ] Release build bundles the profile (ProfileInstaller compiles on install)
- [ ] Benchmark build type is minified, non-debuggable
- [ ] Runs fully offline (local device only)

## Related Files
- `baselineprofile/` — test module (generator + benchmarks)
- `app/build.gradle.kts` — benchmark build type, profileinstaller, consumer plugin
- `app/src/main/baseline-prof.txt` (or generated `baselineProfiles/`) — the shipped profile
- `settings.gradle.kts` — module include

## Notes
- Baseline Profiles benefit API 28+; below that the profile is simply ignored.
- Regenerate the profile after significant changes to the startup path or library screen.
- Keep the generator focused on truly hot paths — startup, first list render, play. Profiling cold/rare screens dilutes the benefit.
- This is purely local tooling; no network, consistent with the offline architecture.
- Pairs well with `setup-static-analysis` and CI — run the benchmark on a schedule, not every PR (it's slow and device-bound).

## Common Pitfalls
- ❌ Benchmarking a debuggable build — results are meaningless; use the minified benchmark build type.
- ❌ No `testTag` on the list — UiAutomator can't drive the scroll, so the profile misses the hot path.
- ❌ Running on a non-AOSP emulator image — macrobenchmark requires a compatible image or a physical device.
- ❌ Forgetting `profileinstaller` — the bundled profile won't be applied on install.
