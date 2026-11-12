---
type: source
summary: "Custom tags phase 4 plan: tag chips in every track list and on Now Playing, and the Find tracks filter screen with live AND results played as the queue."
aliases: ["2026-11-12-custom-tags-phase4-finding"]
tags: [source, custom-tags, plan]
sources: []
updated: 2026-11-12
---
# Custom tags — phase 4 plan (finding and showing)

Custom tags phase 4 plan: tag chips in every track list and on Now Playing, and the Find tracks filter screen with live
AND results played as the queue.

Raw source: `docs/superpowers/plans/2026-11-12-custom-tags-phase4-finding.md`; device-walked by the user 2026-11-12.
The last of the four phases in [[custom-tags-roadmap]].

- Task 0: a scroll baseline on the frames emulator; the end-of-phase A/B stayed within 5 %.
- Task 1: a song-to-tags map (known tags only) and `SongFinder`, an in-memory AND filter over the library
  ([[module-core-data]], [[module-core-database]], [[module-core-model]]: `SongFilter`, `SongCustomTags`).
- Task 2: kit tag chips, a one-line "+N" chip line and a removable chip with their tokens
  ([[module-core-designsystem]]).
- Task 3: chips in every track list through one composition local provided by the app ([[module-core-ui]],
  [[module-app]]); the picker's "New tag" row becomes optional.
- Task 4: chips on Now Playing, centred, at most two lines ([[module-feature-player]]).
- Tasks 5–6: Find tracks — filters saved across process death, removable filter chips (also for tags whose names could
  not load), the picker, live results, Shuffle and Play over the result; a Search button on the Tracks tab's sort header
  ([[module-feature-search]], [[module-feature-library]]).
- Phase 4 writes no files.

## Mentions
[[custom-tags]], [[module-core-data]], [[module-core-database]], [[module-core-model]], [[module-core-designsystem]],
[[module-core-ui]], [[module-app]], [[module-feature-player]], [[module-feature-search]], [[module-feature-library]],
[[song-not-track]], [[one-ui-design-system]]

## Related
[[custom-tags-roadmap]], [[custom-tags-phase3-plan]], [[custom-tags-spec]]
