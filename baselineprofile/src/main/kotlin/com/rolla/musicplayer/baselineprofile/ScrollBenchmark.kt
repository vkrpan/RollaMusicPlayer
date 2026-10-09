package com.rolla.musicplayer.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE_NAME = "com.rolla.musicplayer"
private const val ITERATIONS = 5

/**
 * Measures frame timing while flinging Home's Tracks list -- the first-scroll jank the
 * Baseline Profile targets. Compares [CompilationMode.None] against [CompilationMode.Partial].
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollCompilationNone() = scroll(CompilationMode.None())

    @Test
    fun scrollCompilationBaselineProfile() = scroll(CompilationMode.Partial())

    private fun scroll(compilationMode: CompilationMode) =
        benchmarkRule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = compilationMode,
            iterations = ITERATIONS,
            startupMode = StartupMode.COLD,
            setupBlock = {
                device.grantMediaPermissionAndSeedLibrary(PACKAGE_NAME)
            },
        ) {
            startActivityAndWait()
            val list = waitForSongList()
            flingSongList(list)
        }
}
