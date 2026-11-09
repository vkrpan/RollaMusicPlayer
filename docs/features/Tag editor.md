---
type: feature
status: active
date: 2026-07-07
source: "v1 Phase 4 (`546154d`…`98868cc`); One UI Phase 6b"
tags: [tag-editor, feature]
related: ["[[Features]]", "[[Tag writes go through a cache copy]]", "[[Custom tag system is cut]]", "[[Per-track artwork]]", "[[feature-tageditor]]", "[[tag-editor-agent]]"]
---

# Tag editor

- **Single-track editor (`TagEditor(songId)`):** title, artist, album, album artist, genre, year, track number,
  composer, and artwork from the photo picker (a local image).
- **Batch editor (`BatchTagEditor`):** reached from the Tracks tab's selection bar ("Edit tags"). Per-field apply
  toggles, per-song outcomes, and "Edited X of N tracks" on a partial failure.
- **Formats:** MP3, FLAC and M4A, contract-tested on real fixture files. Artwork only on these three
  (`File.supportsArtwork()`).
- **Old MP3s with only an ID3v1 tag:** the editors show the file's existing text, and a save keeps it; setting a
  picture keeps it too ([[jaudiotagger and ID3v1-only MP3s]], since per-track artwork Task 6).
- **How saves work:** [[Tag writes go through a cache copy]]. Consent comes first per SDK, Cancel and Back are guarded
  mid-save, and `TagSaveFinalizer` runs after a save.
- **Entry points:** "Edit tags" goes through `NavController.openTagEditor(song)`
  (`app/.../navigation/TagEditorNavigation.kt`) from every options sheet and Now Playing's ⋮.
- **After a save:** `TagSaveFinalizer` re-indexes, invalidates each saved song's cached picture (each in its own try,
  even when the re-index fails) and refreshes playback in its own try.
- **Remove artwork (single track, since per-track artwork Task 7):** a destructive tonal pill next to "Change
  artwork", shown only when the file has a picture or one was just picked; hidden for formats that cannot hold one.
  The last tap between Change and Remove wins; the header shows the white note until Save; TalkBack hears "Artwork
  removed". The pills wrap at large font.
- **Set/Remove artwork for a selection:** from the Tracks tab's selection ⋮ (`SelectionArtworkViewModel`,
  `SelectionArtworkActions`; per-track artwork Task 8). The batch editor screen itself has no artwork fields.
- **Skill:** `implement-tag-editor`. **Module:** [[feature-tageditor]]. **Owner:** [[tag-editor-agent]] (IO),
  [[ui-builder]] (screens).
- **History:** `docs/history/v1-phases/PHASE4-TAGEDITOR.md`; the redesign restyle is in Phase 6b
  ([[2026-10-21-oneui-phase6b-search-editors rulings]]).
