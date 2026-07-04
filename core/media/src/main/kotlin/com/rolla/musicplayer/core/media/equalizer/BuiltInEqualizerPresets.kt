package com.rolla.musicplayer.core.media.equalizer

import com.rolla.musicplayer.core.model.EqualizerPreset

/**
 * The app's six built-in equalizer presets, as in-code constants — **not** rows in
 * `equalizer_presets`. They ship with the app, never change, and never need a database round-trip
 * to read.
 *
 * ### Why negative ids
 * Room's `EqualizerPresetDao.insertPreset` auto-generates positive row ids (`@PrimaryKey(autoGenerate
 * = true)`, starting at 1) for user-saved presets. Assigning these built-ins synthetic ids in the
 * negative range (`-1L`..`-6L`) guarantees they can never collide with a real, persisted preset id,
 * without needing to reserve/skip ids in the database. It also lets [EqualizerRepository.deletePreset]
 * cheaply distinguish "built-in, ignore the request" from "user preset, delete it" with a single sign
 * check — no lookup required.
 *
 * ### Ordering
 * [BUILT_IN_EQUALIZER_PRESETS] is a fixed display order (Balanced, Bass boost, Smooth, Dynamic,
 * Clear, Treble boost); [EqualizerRepository.observePresets] always emits these first, followed by
 * the user's saved presets.
 *
 * Every gain list has exactly [TARGET_FREQUENCIES_HZ].size (9) entries, one per project target
 * frequency in [TARGET_FREQUENCIES_HZ] order (40Hz…10kHz), and every gain stays within the
 * conservative ±1500 millibel (±15dB) envelope common to `audiofx.Equalizer` devices.
 */
val BUILT_IN_EQUALIZER_PRESETS: List<EqualizerPreset> = listOf(
    // Flat response: every band at 0mB. The neutral starting point / "off" curve.
    EqualizerPreset(
        id = -1L,
        name = "Balanced",
        isCustom = false,
        gainsMillibel = listOf(0, 0, 0, 0, 0, 0, 0, 0, 0),
    ),
    // Low-shelf boost that tapers to flat by ~630Hz: strong lift at 40/80Hz, easing off through
    // 160/315Hz, back to 0 from 630Hz up.
    EqualizerPreset(
        id = -2L,
        name = "Bass boost",
        isCustom = false,
        gainsMillibel = listOf(600, 500, 350, 150, 0, 0, 0, 0, 0),
    ),
    // A mild, gentle V: small bass lift, a shallow mid dip centered around 630Hz, and a soft
    // (low-magnitude) high lift — noticeably less pronounced than "Dynamic" at both ends.
    EqualizerPreset(
        id = -3L,
        name = "Smooth",
        isCustom = false,
        gainsMillibel = listOf(200, 150, 50, -50, -100, -50, 50, 100, 100),
    ),
    // A pronounced V: bass and treble both pushed hard, with a deeper mid scoop than "Smooth" —
    // the classic "loud, exciting" curve.
    EqualizerPreset(
        id = -4L,
        name = "Dynamic",
        isCustom = false,
        gainsMillibel = listOf(700, 600, 300, -100, -200, -100, 300, 600, 700),
    ),
    // Upper-mid / presence lift centered around 1.25kHz-5kHz (vocal clarity), with bass pulled back
    // slightly to keep the low end from masking it.
    EqualizerPreset(
        id = -5L,
        name = "Clear",
        isCustom = false,
        gainsMillibel = listOf(-100, -50, 0, 0, 100, 300, 500, 400, 200),
    ),
    // Rising high-shelf that starts lifting at ~1.25kHz and climbs to its strongest boost at
    // 10kHz; everything at/below 630Hz stays flat.
    EqualizerPreset(
        id = -6L,
        name = "Treble boost",
        isCustom = false,
        gainsMillibel = listOf(0, 0, 0, 0, 0, 200, 400, 600, 800),
    ),
)
