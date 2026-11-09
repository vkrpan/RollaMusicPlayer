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
