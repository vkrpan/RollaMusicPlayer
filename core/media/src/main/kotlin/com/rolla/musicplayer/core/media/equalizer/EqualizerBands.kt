package com.rolla.musicplayer.core.media.equalizer

/**
 * The project's canonical target-frequency ladder, in Hz: a one-octave sweep from 40Hz to 10kHz.
 *
 * NOTE: this is **nine** values, not eight. `CLAUDE.md`'s "8-band graphic equalizer" label is a
 * known miscount inherited from the product spec; this implementation follows the full
 * nine-frequency ladder defined in `.claude/skills/implement-equalizer/SKILL.md` and every caller
 * (UI, persistence, presets) works against all nine labels. Do not truncate this list to match the
 * "8-band" wording.
 *
 * ### Device band mapping (read before wiring persistence or presets)
 * `android.media.audiofx.Equalizer` almost never exposes nine independent bands — most devices
 * expose far fewer (AOSP's software EQ and common OEM DSPs typically expose ~5).
 * [EqualizerController.setGainForFrequency] resolves each target frequency to the *nearest*
 * device band via `Equalizer.getBand(frequencyMilliHz)`; it never assumes a fixed band count and
 * always queries `numberOfBands` / `bandLevelRange` from the live effect (see [DeviceEqualizer]).
 *
 * On a typical 5-band AOSP-style device (center frequencies approximately 60 / 230 / 910 / 3600 /
 * 14000 Hz), nearest-band resolution pairs the nine targets up like this:
 *
 * | Device band (center) | Target frequencies routed to it |
 * |---|---|
 * | 60 Hz    | 40 Hz, 80 Hz |
 * | 230 Hz   | 160 Hz, 315 Hz |
 * | 910 Hz   | 630 Hz, 1250 Hz |
 * | 3600 Hz  | 2500 Hz, 5000 Hz |
 * | 14000 Hz | 10000 Hz |
 *
 * **Consequence — last write wins on shared bands.** Every pair above (all except 10000 Hz) maps
 * two logical target frequencies onto a single physical band, which only has one gain. Calling
 * [EqualizerController.setGainForFrequency] for both members of a pair does not blend or average
 * them — whichever call happens LAST silently overwrites the earlier one for that band. A caller
 * that applies a full nine-gain sweep (e.g. restoring persisted settings, or a preset) must be
 * aware the effective, audible result is only as many independent gains as the device actually has
 * (typically five), not nine. A future batched/averaged apply (e.g. average the two logical gains
 * before writing the shared band) can be layered on top of this controller without changing this
 * mapping contract; this controller intentionally keeps last-write-wins semantics and does not
 * average, so behavior stays simple and predictable to reason about from the call order alone.
 */
val TARGET_FREQUENCIES_HZ = intArrayOf(40, 80, 160, 315, 630, 1250, 2500, 5000, 10000)
