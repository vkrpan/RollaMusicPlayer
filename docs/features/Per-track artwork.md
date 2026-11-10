---
type: feature
status: done
date: 2026-11-08
source: "user report 2026-11-08 (\"after save the default artwork stays\")"
tags: [artwork, tag-editor, feature]
related: ["[[Features]]", "[[Artwork comes from the track's own file]]", "[[Albums, artists and folders show the placeholder]]", "[[Set artwork for many tracks from the selection menu]]", "[[Untagged tracks share a MediaStore album]]", "[[Tag editor]]", "[[Backlog]]"]
spec: "docs/superpowers/specs/2026-11-08-per-track-artwork-design.md"
plan: "docs/superpowers/plans/2026-11-08-per-track-artwork.md"
---

# Per-track artwork

**Status (2026-11-09):** **built, device-verified, committed (`e129f3e`) and pushed** to `feat/oneui-redesign` (2026-11-09). All nine tasks done subagent-driven (user's
choice), each reviewed; final whole-branch review: ready to merge after one fix wave; the user's device walk on the
Motorola passed ("everything seems perfect"). Ledger: `.superpowers/sdd/2026-11-08-per-track-artwork/progress.md` (pre-flight rulings
P1–P6, later P7–P9; archived as [[2026-11-08-per-track-artwork rulings]]). Ruling P9: `SongArtworkCache.get` can throw on a disk
write failure, so every caller catches non-cancellation exceptions and shows the placeholder.

| Task | State |
|---|---|
| 1 Per-track artwork value | done (review clean). Until Tasks 3–4 land, tracks transiently show the placeholder everywhere |
| 2 Per-track cache | done (review clean; four minors deferred to the final review). Added alongside the album cache (ruling P1); the album cache goes in Task 6 |
| 3 Coil + widget | done (review clean after one test-only fix round). Lists, Search, queue, mini-player, Now Playing and the widget read the track's own picture again |
| 4 Notification bitmap loader | done (review clean after two fix rounds). Notification, lock screen and live card use the loader; the resume card always shows the white note (user decision, ruling P14; fix round 3). Not yet device-checked |
| 5 Groups show the placeholder | done (review clean; device DAO run 104/0 on the Motorola, 2026-11-09) |
| 6 Tag IO + finalizer | done (review clean after three fix rounds: ID3v1-only MP3s, FLAC Vorbis-comment pictures, rulings P15–P19). The album cache is deleted |
| 7 Edit tags: Remove artwork | done (review clean after one fix round: wraps at large font, last tap wins, TalkBack says "Artwork removed"; rulings P20–P22) |
| 8 Selection: Set/Remove artwork | done (review clean after two fix rounds: selection frozen while writing, title fits at large font; rulings P23–P25) |
| 9 Docs, gate, device walk | docs done; release gate green; device tests 125/0; final review: ready to merge after one fix wave (widget refresh after a paused save, rulings P27–P28); installed on the Motorola 2026-11-09; device walk passed (user, 2026-11-09) |

**The bug that started it:**
- **Report:** after choosing a picture in Edit tags and saving, the old artwork stayed. After a rescan, the picture
  appeared on every untagged track and could not be changed.
- **Root cause:** the save worked, but the app showed MediaStore's per-**album** cover
  ([[Untagged tracks share a MediaStore album]]).

**What gets built:** [[Artwork comes from the track's own file]],
[[Albums, artists and folders show the placeholder]], [[Set artwork for many tracks from the selection menu]].

**Plan in brief (nine tasks):**
1. A per-track artwork value `rolla-artwork://song/{id}?v={dateModified}`, derived at read time with no Room migration.
2. A per-track embedded-picture cache (`SongArtworkCache`) that also remembers "no picture".
3. Coil interceptor and widget loader.
4. Media3 `BitmapLoader` with a placeholder bitmap.
5. Groups show the placeholder; album rows show pictures; playlist cards take the first four tracks.
6. `TagWriter.removeArtwork` and per-track invalidation.
7. Edit tags: Remove artwork.
8. Tracks tab: Set/Remove artwork.
9. Docs, the gate, and the user's device walk.

**Modules:** [[core-model]], [[core-database]], [[core-data]], [[core-media]], [[core-ui]], [[core-designsystem]],
[[feature-library]], [[feature-playlists]], [[feature-search]], [[feature-tageditor]], [[feature-widget]], [[app]].

**Known limit:** two saves to one file within one second keep the old picture on screens already composed (plan
ruling R6).
