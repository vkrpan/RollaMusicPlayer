package com.rolla.musicplayer.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE_NAME = "com.rolla.musicplayer"
private const val ITERATIONS = 10

/**
 * Compares cold-start time with [CompilationMode.None] (no profile -- worst case) against
 * [CompilationMode.Partial] (Baseline Profile applied) to quantify the win from
 * `:app:generateBaselineProfile`. Device/emulator-bound and slow -- run on demand or on a
 * schedule, never on every PR (see .claude/skills/generate-baseline-profile/SKILL.md).
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun startupCompilationNone() = startup(CompilationMode.None())

    @Test
    fun startupCompilationBaselineProfile() = startup(CompilationMode.Partial())

    private fun startup(compilationMode: CompilationMode) =
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            iterations = ITERATIONS,
            startupMode = StartupMode.COLD,
            setupBlock = {
                // Idempotent: permission grant no-ops if already granted, seeding no-ops once the
                // MediaStore is non-empty.
                device.grantMediaPermissionAndSeedLibrary(PACKAGE_NAME)
            },
        ) {
            pressHome()
            startActivityAndWait()
        }
}
