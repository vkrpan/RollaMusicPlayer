# Per-track artwork — design

Date: 2026-11-08 · Status: draft for the user's review · Branch: `feat/oneui-redesign`

## 1. Intent

A track's picture is the one stored **inside its own audio file**. Rolla Music shows that picture for the track
everywhere, and the tag editor changes or removes it. Today the app shows the cover Android keeps for the track's
*album*, so:

- every track without an album tag shows the same picture (Android files them all under an album named after their
  folder, e.g. "Download");
- a picture saved into one file appears on every other track of that album after a rescan, and cannot then be changed
  or removed from the app;
- a picture saved in the tag editor does not appear after Save.

The user's words: "I want artwork to be saved to the audio file itself, not the album Android links it to."

## 2. Evidence (Motorola edge 70, API 36, read-only probes, 2026-11-08)

| Track | MediaStore album | `album_id` | Embedded front cover in the file |
|---|---|---|---|
| sunrise house mix | Download | 1540603544537963427 | 4096×2304 JPEG, own picture |
| Blackwater (Octave One) | Download | 1540603544537963427 | 3840×2160 JPEG, own picture |
| Congo to Cuba | albumm | 7372593584554650381 | 4096×2304 JPEG, own picture |
| Another Chance (Roger Sanchez) | albumm | 7372593584554650381 | 2040×1148 JPEG, own picture |

- The save path works: each file holds exactly one, distinct front cover (ID3v2.4 `APIC`, type 3).
- `content://media/external/audio/albumart/{albumId}` (what `MediaScanner.kt:65` stores as `Song.artworkUri`, and what
  `AlbumArtworkCache` decodes) returns one picture per album: the "albumm" album returns exactly Congo to Cuba's
  thumbnail (byte-identical to `…/audio/media/9541/albumart`).
- `content://media/external/audio/media/{id}/albumart` returns a different thumbnail per track. It is **not** used by
  this design (§5.1): what it returns for a file with no picture of its own, and how long it stays stale after a write,
  are unverified.

## 3. Goals

1. **Tracks show their own file's picture** in every place a track appears: Tracks, Favourites, playlist pages,
   smart-playlist pages, album/artist/folder pages' track rows, Search track results, the queue sheet, the mini-player,
   Now Playing, the media notification and lock screen, and the home-screen widget.
2. **No picture in the file → the white note on black**, exactly today's placeholder. Never another track's picture,
   never the album's cover, never a folder image. This includes the media notification, lock screen and System UI
   media card (user, 2026-11-08): for a track without a picture the Media3 bitmap loader returns a bitmap of the
   placeholder (white note glyph on black), where today they show no artwork.
2a. **Album pages show track pictures** (user, 2026-11-08): album page rows show each track's own picture like every
   other track list, instead of the track number (`AlbumDetailScreen` stops passing `trackNumber`; the number is not
   shown — Claude's call).
3. **Albums, artists and folders always show the placeholder**: album cards and rows, the album page header, the artist
   page header and its album strip, artist rows, folder rows, and Search's album and artist results.
4. **Edit tags (one track)**: the header card gets **Remove artwork** next to **Change artwork** (mockup approved,
   option kept). Remove previews the placeholder at once; the file changes only on Save, like every other edit. Remove
   is hidden when the track shows no picture (none in the file and none pending). Picking a picture after Remove, or
   Remove after a pick, leaves the last choice in force.
5. **Tracks tab multi-select**: the selection bar keeps "Edit tags" and gains a ⋮ menu holding **Set artwork** and
   **Remove artwork** (destructive colour) — option A of the approved mockup.
   - **Set artwork:** the system photo picker; the picked image is validated with the existing `ArtworkLoader` rules;
     then one Android write-permission prompt for all selected files; then the picture is written into every selected
     file.
   - **Remove artwork:** a confirmation dialog ("Remove artwork from N tracks?", Remove / Cancel), then the same
     one-prompt-then-write flow, deleting every picture in each file.
6. **Changes show at once.** After any save (one track or many) every screen, the notification and the widget show the
   new picture or the placeholder without a rescan or an app restart.
7. **Existing libraries fix themselves.** After the update, the user's tracks show their own pictures without any manual
   step.

8. **Playlists tab feature cards** (Favourite tracks, Recently played, Most played) keep their four-tile collage, built
   from the collection's first four tracks; each tile shows that track's own picture or the placeholder. No background
   scan to find tracks that have pictures (Claude's call).
9. **Formats.** Set and Remove artwork apply to MP3, FLAC and M4A, the formats the tag editor is contract-tested on.
   For any other format the tag editor hides Change and Remove artwork, and a multi-track Set or Remove counts those
   tracks as not written (the "K of N" message), so the app never reports a picture it cannot read back (Claude's
   call).

The mockup the user approved (2026-11-08) is described in full by goals 4 and 5 and §5.7; it lived only in a
git-ignored brainstorm folder, so the spec text is the binding record: the selection bar reads "N selected" with ✕ on
the left and "Edit tags" plus ⋮ on the right, the ⋮ menu lists Set artwork then Remove artwork (destructive text); the
editor header card shows the artwork, then the two tonal pills Change artwork and Remove artwork (destructive text)
side by side, then title and artist.

## 4. Non-goals and anti-goals

- **Not** in the batch tag editor screen: pictures for many tracks go only through the Tracks tab's selection bar
  (user, 2026-11-08). Multi-select stays on the Tracks tab only.
- **Not** folder images (`cover.jpg` and similar) or online artwork. The app stays offline.
- **Anti-goal:** any path where a track shows a picture that is not in its own file. That is the bug this fixes.
- **Anti-goal:** list scrolling getting slower. The Tracks list already has an open scroll regression (CLAUDE.md
  backlog); this change must not add main-thread work or a file read per bind once a picture is cached.

## 5. Decisions

### 5.1 Where the picture comes from (Claude's call)

The picture is read from the file's own tags with `MediaMetadataRetriever.getEmbeddedPicture()` on the track's
`content://` uri, downscaled, and cached on disk **per track** (keyed by the MediaStore song id). This replaces the
per-album `AlbumArtworkCache` and its decoder from `…/audio/albumart/{albumId}`. Reasons: it reads exactly what the
tag editor writes, a file without a picture gives "no picture" (goal 2) rather than whatever Android substitutes, and
freshness is under the app's control (§5.3).

- A track with **no** picture must be remembered as such (a negative entry), so binds never re-open the file to learn
  the same answer again.
- The existing cache's guarantees carry over: decode off the main thread, publish by atomic rename, a fixed byte budget
  with write-age eviction, a synchronous `peek` for the image loader's fast path.

### 5.2 How screens ask for it

`Song.artworkUri` stays a `String` and becomes a **per-track** value that identifies the track, so every existing caller
(the Coil interceptor, `toMediaItem()`, the widget's state, Now Playing) keeps passing one string. The builder picks the
exact value; constraints:

- it must be distinct per track, survive Room storage and Media3's `MediaMetadata.artworkUri`, and parse back to the
  song id;
- every in-app load resolves it through the per-track cache (the Coil interceptor, the widget loader);
- **Media3** must load it through the same cache: give the `MediaLibrarySession` a `BitmapLoader` that resolves it
  (notification, lock screen, System UI media card); if the value is a real `content://` uri, the default loader must
  never be the one that answers for it;
- album, artist and folder models stop carrying a picture (their `artworkUri` becomes empty, or the field goes; the
  builder's call), so goal 3 holds by construction, including `Artist.artworkUri`'s "first album's cover" rule, which is
  dropped.

### 5.3 Freshness

- **After a save in this app** (one track, Remove, or a multi-track Set/Remove), `TagSaveFinalizer` drops each saved
  track's cached entry (positive or negative) before it refreshes playback, so the next load reads the file again. The
  refresh step runs even if dropping an entry fails (this closes v1.0 review follow-up #1 for the new cache).
- Visible images must actually change: a screen that keeps its composition across the save (Now Playing behind the
  editor, the widget's decode memo, Media3's notification bitmap) must load again. Every consumer that holds a decoded
  bitmap keyed by the unchanged string subscribes to the cache's invalidations or keys on the cache file's
  last-modified. (v1.0 review follow-up #3, the invalidation flow with no subscriber, is resolved by giving it its
  consumers or removing it.)
- **Changes made by other apps** are picked up by Settings → Rescan library, which clears the whole cache (as today).

### 5.4 Migration (goal 7)

Rows already in Room carry `…/audio/albumart/{albumId}` values. The update must not show album covers for them. The
builder chooses between a Room migration (v5 → v6) that rewrites the column, or deriving the per-track value from the
song id at read time and ignoring the stored column; either way `MigrationTest` and the library sync keep passing and
the album-art values never reach a screen.

### 5.5 Multi-track writes (Claude's call, following the batch tag editor)

- The write runs in a ViewModel scope, not the composable's; the selection bar shows progress while it runs, and the
  selection stays until the write ends.
- One write-permission prompt covers every selected file (`MediaWriteRequester.ensureWritable` with all their uris;
  API 29 recovery as the batch editor does it). Declining writes nothing and keeps the selection.
- Files are written one at a time through the same copy-through-cache path as the tag editor
  (`SongFileResolver` → `TagWriter` → persist). A failure on one file does not stop the others.
- When done: all succeeded → the selection clears and a short message says "Artwork set on N tracks" / "Artwork removed
  from N tracks"; some failed → "Artwork set on K of N tracks" and the selection keeps only the failed tracks.
- `TagSaveFinalizer.onSongsSaved` runs once for the succeeded tracks.

### 5.6 Writing and removing in the file

- Set: the existing `TagWriter.writeArtwork` (deletes every existing picture, then writes one front cover). Unchanged.
- Remove: a new `TagWriter.removeArtwork(file)` that deletes every picture field and commits; contract-tested on the
  same MP3/FLAC/M4A fixtures as `writeArtwork`, asserting no picture remains.

### 5.7 UI copy and components

UI copy: "Change artwork", "Remove artwork", "Set artwork", "Remove artwork from N tracks?", "Remove", "Cancel",
"Artwork set on N tracks", "Artwork set on K of N tracks", "Artwork removed from N tracks", "Artwork removed from K of
N tracks". Built only from the kit (`OneUiPillButton` tonal with destructive text for Remove, `OneUiIconButton` ⋮,
`OneUiDropdownMenu`/`OneUiMenuItem`, `OneUiDialog` + `OneUiTextButton(destructive = true)`); no new tokens unless the
spec's §5 table gains the row first.

## 6. Verification the user will see

- Unit/contract tests: the per-track cache (hit, miss, negative entry, invalidate, clear, eviction), `removeArtwork` on
  the three fixtures, the multi-track ViewModel (all succeed, partial failure, decline), the finalizer's ordering and
  failure isolation, the Media3 bitmap loader resolving a per-track value.
- A Compose test proving two tracks of one album show different pictures and an album card shows the placeholder.
- **Device walk on the Motorola, done by the user's hand** (the agent never saves tags on that phone): the four tracks
  in §2 show four different pictures before any edit; set a picture on Blackwater and see it at once in Tracks, Now
  Playing, the notification and the widget, while sunrise house mix keeps its own; remove it and see the white note
  in all four, the notification included;
  multi-select two tracks and Set, then Remove; the Albums tab shows the white note for "Download" and "albumm".

## 7. Left to the builder

The exact per-track `artworkUri` value; the cache's file naming and size budget; migration vs read-time derivation
(§5.4); where the multi-track ViewModel lives (Home route or a new `:feature:tageditor` class) as long as module
dependency rules hold; test names.
