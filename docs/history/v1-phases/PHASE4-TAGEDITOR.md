# Phase 4 — Tag Editor

Adds ID3 / metadata editing (title, artist, album, album artist, genre, year, track number, composer, and
artwork), single and batch, written back to the user's local files. This is the **only** feature that
modifies files, so it revolves around the **scoped-storage write-consent** flow — and it re-indexes the
library afterward so the UI and play counts stay correct. Everything is local: no online metadata/artwork
lookups, no INTERNET permission, and NOT the WRITE_EXTERNAL_STORAGE permission.

Branch: `git checkout -b feature/tageditor`. One prompt at a time → ✅ checkpoint → commit. Follow the
`implement-tag-editor` skill; the tag-editor-agent owns `:feature:tageditor`.

---

## Step 0 — Re-sync (no code)

```
Starting the tag editor on branch feature/tageditor. Read CLAUDE.md, OWNERSHIP.md, model-vocabulary.md,
and the implement-tag-editor skill. Inspect :core:model, :core:data (scanner/repository), and how a Song
currently stores its file reference (content:// uri and/or path). Report: (1) how we resolve a writable
File/uri from a Song per SDK level; (2) the scoped-storage write path we'll use (createWriteRequest on
30+, RecoverableSecurityException on 29, direct on ≤28); (3) the re-index hook the media-scanning-agent
exposes so we can refresh Room after a write. Note: the editable model is SongTags (NOT TrackTags — per
model-vocabulary.md). Don't write code — give me the plan and the exact write/consent strategy.
```
✅ **Checkpoint:** clear file-resolution + per-SDK consent plan + the re-index hook to call after writes.

## Prompt 1 — Tag library + model (build-tooling-agent + tag-editor-agent)

```
Use the build-tooling-agent to add an OFFLINE, on-device tag library to gradle/libs.versions.toml — an
Android-compatible JAudiotagger fork (ID3v2/MP3, Vorbis/FLAC-OGG, MP4/M4A-AAC). Confirm it is local-only
(no network/telemetry) so checkNoNetwork still passes; no INTERNET permission is added. Then, with the
tag-editor-agent, define the SongTags model in :feature:tageditor (title, artist, album, albumArtist,
genre, year, trackNumber, composer — strings to allow blank/partial input). Build.
```
✅ **Checkpoint:** dependency added and vetted (checkNoNetwork green); `SongTags` compiles.
`git commit -m "build+feat(tageditor): offline tag library + SongTags model"`

## Prompt 2 — Reader + Writer (tag-editor-agent)

```
Use the tag-editor-agent. In :feature:tageditor implement:
- TagReader.read(file): SongTags — populate all fields via the tag library.
- TagWriter.write(file, tags: SongTags) — write fields (setFieldIfNotBlank; blank clears the field
  intentionally), then commit.
- A file-resolution helper turning a Song into the writable File/uri per SDK (coordinate with
  media-scanning-agent on what's stored).
All file IO on Dispatchers.IO — never the main thread. Add unit tests against small fixture audio files
(MP3/FLAC/M4A). Build.
```
✅ **Checkpoint:** read populates all fields across formats; write persists and survives a re-read (test).
`git commit -m "feat(tageditor): TagReader + TagWriter"`

## Prompt 3 — Scoped-storage write consent (tag-editor-agent)

```
Use the tag-editor-agent. Implement MediaWriteRequester using
ActivityResultContracts.StartIntentSenderForResult:
- API 30+: MediaStore.createWriteRequest(resolver, uris) → launch the IntentSender → write on RESULT_OK.
- API 29: attempt write, catch RecoverableSecurityException, launch its IntentSender, retry on grant.
- API ≤28: direct write.
The actual file write must happen AFTER consent. Declining consent writes nothing and surfaces a clear
message. Build.
```
✅ **Checkpoint:** on 30+ a write prompts once and then writes; declining writes nothing; 29/≤28 paths handled.
`git commit -m "feat(tageditor): scoped-storage write consent"`

## Prompt 4 — Single-edit screen + navigation (`:feature:tageditor` + `:app`)

```
Use the ui-builder, viewmodel-architect, tag-editor-agent, and navigation-agent. Add a type-safe route
TagEditor(songId: Long). Build TagEditorScreen per ui-style-guide (form layout, M3 TextFields, design
tokens): album-art preview on top, editable fields (title/artist/album/album artist/genre/year/track
number/composer) with light validation (year/track numeric), Save + Cancel. TagEditorViewModel loads tags
via TagReader, holds edits, and on Save runs the consent flow then TagWriter. Wire an "Edit tags" entry
from the SongListItem overflow. Cancel discards. Build and verify navigation.
```
✅ **Checkpoint:** editing one song from its overflow saves changes to the file after consent; Cancel discards.
`git commit -m "feat(feature-tageditor): single-song editor + route"`

## Prompt 5 — Batch editing (tag-editor-agent + ui-builder)

```
Use the tag-editor-agent and ui-builder. Add multi-select in the library (long-press → selection mode)
→ "Edit tags" for the selection. In batch mode, each field has an "apply this field" toggle so unpicked
fields are left untouched per file; only picked fields are written to all selected songs. Request write
consent ONCE for all URIs (createWriteRequest with the full list), then write each file on Dispatchers.IO
with progress feedback. Build.
```
✅ **Checkpoint:** batch edit writes only the chosen fields across files with a single consent prompt.
`git commit -m "feat(feature-tageditor): batch editing"`

## Prompt 6 — Re-index after save + refresh current/widget (media-scanning-agent + audio-engineer)

```
Use the media-scanning-agent + tag-editor-agent + audio-engineer. After any successful write, re-read the
affected file(s) and update the corresponding Room rows (targeted re-sync — do not full-rescan), and call
MediaScannerConnection.scanFile so other apps see the change. If an edited song is the current track,
refresh the now-playing metadata and the widget state. Build and verify the library reflects new tags
without a manual rescan.
```
✅ **Checkpoint:** edited tags appear immediately in the library; current track/widget update if edited.
`git commit -m "feat(feature-tageditor): re-index + current-track/widget refresh after save"`

## Prompt 7 — Artwork editing (optional)

```
Use the tag-editor-agent + ui-builder. Allow replacing embedded artwork from a LOCAL image
(PhotoPicker / OpenDocument) — read the picked image, write it via the tag library's artwork API, using
the same scoped-storage consent flow. Source images locally only; no online art. Build.
```
✅ **Checkpoint:** artwork can be replaced from a local image and persists in the file.
`git commit -m "feat(feature-tageditor): artwork editing"`

## Prompt 8 — Review gate + tests

```
Run code-reviewer, compose-performance-auditor, and test-writer in parallel. Fix all CRITICAL/HIGH:
offline/no-network + no online metadata/artwork lookup, NO INTERNET and NO WRITE_EXTERNAL_STORAGE on
modern Android, write-only-after-consent, all file IO on Dispatchers.IO, re-index after save, and no
hardcoded visuals bypassing :core:designsystem / ui-style-guide.md. Coverage: TagReader/TagWriter against
fixtures, the consent-flow branching (per SDK), batch per-field logic, and TagEditorViewModel (Turbine).
Re-run ./gradlew check until green.
```
✅ **Checkpoint:** no CRITICAL/HIGH; `./gradlew check` green; reader/writer + consent + ViewModel covered.
`git commit -m "test(feature-tageditor): coverage + review fixes"`

Then merge `feature/tageditor` and update CLAUDE.md "Current Status".

---

## Notes & gotchas
- **Consent before write, always.** On Android 10+ writing before the IntentSender grant throws
  SecurityException. The write must run inside the consent callback.
- **Batch = one prompt.** Batch all URIs into a single `createWriteRequest`; never prompt per file.
- **No WRITE permission.** WRITE_EXTERNAL_STORAGE is ignored on API 30+ and must not be added — this uses a
  per-edit write request, not a runtime permission (permissions-agent owns the read permission only).
- **Blank handling is intentional.** Decide clear-vs-keep for empty fields and keep it consistent
  (setFieldIfNotBlank clears; document it in the UI).
- **Re-index or it looks stale.** The library shows old tags until the row is re-read — always re-index the
  affected files after a successful write.
- **IO threading.** Tag commits can be slow on large files — everything on Dispatchers.IO.
- **Library must be offline.** The tag library runs on-device with no telemetry; build-tooling-agent vets it
  and `checkNoNetwork` must still pass.

## Next after tag editor
`:feature:search` (local search over the library — Room FTS or filtered query), then `:feature:settings`,
then `:feature:widget`, then performance polish. Tell me "tag editor done" and I'll write the next phase.
```
