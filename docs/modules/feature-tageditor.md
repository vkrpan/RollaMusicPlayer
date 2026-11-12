---
type: module
status: active
gradle: ":feature:tageditor"
owners: ["[[tag-editor-agent]]", "[[ui-builder]]"]
tags: [module]
related: ["[[Modules]]", "[[Tag editor]]", "[[Per-track artwork]]", "[[Tag writes go through a cache copy]]", "[[Set artwork for many tracks from the selection menu]]"]
---

# feature-tageditor

`:feature:tageditor` — The single and batch tag editors, tag IO (`TagReader`, `TagWriter`, `SongFileResolver`, `MediaWriteRequester`, `ArtworkLoader`), `TagSaveFinalizer`.

- **Owner agent(s):** [[tag-editor-agent]], [[ui-builder]]
- **Features:** [[Tag editor]], [[Per-track artwork]]
- **Decisions and rules:** [[Tag writes go through a cache copy]], [[Set artwork for many tracks from the selection menu]]

## Changes
- 2026-11-08 (per-track artwork Task 6): `TagWriter.removeArtwork`, `TagReader.hasArtwork`, `File.supportsArtwork()`
  (mp3/flac/m4a); shared `AudioFile.editableTag()` builds the ID3v2.3 tag from an ID3v1-only MP3's tag for read,
  writeFields and writeArtwork (rulings P16, P18); FLAC Vorbis-comment pictures are seen and removed (P17, P19).
  `TagSaveFinalizer` takes `SongArtworkCache`, invalidates per song in its own try, refreshes in its own try (P15).
- 2026-11-09 (per-track artwork Task 7): `TagEditorUiState` gains `isArtworkRemoved`, `canRemoveArtwork`,
  `isArtworkSupported`, `previewArtworkUri`; `onRemoveArtworkClick`; a pick generation guard (last tap wins, P21);
  the header's artwork actions are a `FlowRow` (P20) with a polite live region (P22).
- 2026-11-09 (per-track artwork Task 8): `SelectionArtworkViewModel` (per-song loop modelled on the batch editor:
  copy → supportsArtwork → write/remove → persist → delete copy; consent, API 29 recovery; finalizer once with the
  succeeded songs) and `SelectionArtworkActions` (⋮ menu, picker, confirm dialog, progress ring); `SelectionArtworkResult`.
- 2026-11-09 (custom tags phase 1, Task 1): `TagAccess.kt` (`editableTag`, `hasAnyArtwork`, `deleteEveryArtwork`,
  `setFieldIfNotBlank`) moved to [[core-tagio]]; the audio test fixtures moved to [[core-testing]]
  (`core.testing.audio`). Depends on `:core:tagio`. Known existing bug: an M4A save drops other apps' freeform atoms
  ([[jaudiotagger cannot write unknown M4A freeform atoms]], [[Backlog]]).
- 2026-11-10 (custom tags phase 2, Tasks 4–5): both editors host the custom tag picker. Single editor: reads the ticks
  from the editable copy at load; writes `ROLLA_TAGS` after the fields and artwork, before the single persist, only when
  the ticks changed (Ruling 7), as `ticked ∪ unknown IDs from the copy`; an unreadable field disables the rows and the
  fields still save (Ruling 8); a failing tag table reads as empty lists. Batch editor: adds the chosen IDs to each
  MP3/FLAC (never removes; skips songs that already carry them); an unwritable format with tags chosen fails that song
  before any write ("Edited X of N tracks."). The picker handling is shared (final fix wave, `CustomTagEditing.kt`:
  `CustomTagEditingHandlers` with the lists, the 8 handlers, the create guard and the refusal copy; `write` recomputes
  the save state only when the ticks change). The single editor reads `ROLLA_TAGS` only for formats the scan reads
  (MP3, FLAC, M4A); OGG/WAV get empty ticks without opening the field.
- 2026-11-11 (custom tags phase 3, Tasks 2–5): package `managetags`: `ManageCustomTagsViewModel`/`UiState`/`Screen`/
  `Route` (rename, delete with consent and the per-file write loop modelled on `SelectionArtworkViewModel`, "Updated X
  of N tracks."; export/import through `CustomTagListDocuments`, SAF text IO with a BOM strip and a 4 MiB cap, bound by
  a Hilt module in the same file). Both editors get a "Manage tags" link through the picker and prune ticks to the
  known IDs when the lists change (never on the lists' error fallback). The single editor reserves every ID it reads
  at load (`reserveIds`) and saves `(current − userUnticked) ∪ userTicked`, `current` read from a fresh copy of the
  original at save (final review fix; pruned IDs count as untouched). See [[Custom tags]].
